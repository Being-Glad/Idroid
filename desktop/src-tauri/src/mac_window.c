#include <CoreGraphics/CoreGraphics.h>
#include <string.h>

int find_scrcpy_window(double *out_x, double *out_y, double *out_w, double *out_h) {
    CFArrayRef list = CGWindowListCopyWindowInfo(kCGWindowListOptionOnScreenOnly | kCGWindowListExcludeDesktopElements, kCGNullWindowID);
    if (!list) return 0;
    CFIndex count = CFArrayGetCount(list);
    int found = 0;
    for (CFIndex i = 0; i < count; i++) {
        CFDictionaryRef dict = (CFDictionaryRef)CFArrayGetValueAtIndex(list, i);
        CFStringRef owner = (CFStringRef)CFDictionaryGetValue(dict, kCGWindowOwnerName);
        if (!owner) continue;
        char owner_buf[128] = {0};
        CFStringGetCString(owner, owner_buf, sizeof(owner_buf), kCFStringEncodingUTF8);

        CFStringRef title = (CFStringRef)CFDictionaryGetValue(dict, kCGWindowName);
        char title_buf[256] = {0};
        if (title) {
            CFStringGetCString(title, title_buf, sizeof(title_buf), kCFStringEncodingUTF8);
        }

        // Explicitly exclude the main Idroid desktop app window
        if (strcmp(title_buf, "Idroid") == 0) continue;
        // Explicitly exclude the floating dock itself and doctor window
        if (strstr(title_buf, "Idroid Navigation Dock") != NULL || strstr(title_buf, "mirror-dock") != NULL) continue;
        if (strstr(title_buf, "Idroid Doctor") != NULL) continue;

        // Only match genuine scrcpy / phone mirror instances:
        // Either the window owner is "scrcpy", or the title format matches "Idroid • ..."
        int is_scrcpy_owner = (strcasestr(owner_buf, "scrcpy") != NULL);
        int is_mirror_title = (strstr(title_buf, "Idroid •") != NULL || strstr(title_buf, "Idroid -") != NULL);

        if (is_scrcpy_owner || is_mirror_title) {
            CFDictionaryRef bounds = (CFDictionaryRef)CFDictionaryGetValue(dict, kCGWindowBounds);
            if (bounds) {
                CGRect r;
                if (CGRectMakeWithDictionaryRepresentation(bounds, &r)) {
                    if (r.size.width >= 150.0 && r.size.height >= 250.0) {
                        *out_x = r.origin.x;
                        *out_y = r.origin.y;
                        *out_w = r.size.width;
                        *out_h = r.size.height;
                        found = 1;
                        break;
                    }
                }
            }
        }
    }
    CFRelease(list);
    return found;
}

#include <objc/message.h>
#include <objc/runtime.h>

extern void on_mac_notification_reply(const char *notif_id, const char *reply_text);
extern void on_mac_call_notification_action(const char *action, const char *number);

static id s_notif_delegate = nil;

static void notif_center_did_activate(id self, SEL _cmd, id center, id notification) {
    (void)self; (void)_cmd; (void)center;
    if (!notification) return;
    long actType = ((long (*)(id, SEL))objc_msgSend)(notification, sel_registerName("activationType"));

    id userInfo = ((id (*)(id, SEL))objc_msgSend)(notification, sel_registerName("userInfo"));
    Class nsStrClass = objc_getClass("NSString");

    // Check if this is an incoming call notification
    if (userInfo) {
        id keyType = ((id (*)(id, SEL, const char*))objc_msgSend)((id)nsStrClass, sel_registerName("stringWithUTF8String:"), "type");
        id typeObj = ((id (*)(id, SEL, id))objc_msgSend)(userInfo, sel_registerName("objectForKey:"), keyType);
        if (typeObj) {
            const char *utf8_type = (const char*)((const char* (*)(id, SEL))objc_msgSend)(typeObj, sel_registerName("UTF8String"));
            if (utf8_type && strcmp(utf8_type, "call") == 0) {
                id keyNum = ((id (*)(id, SEL, const char*))objc_msgSend)((id)nsStrClass, sel_registerName("stringWithUTF8String:"), "number");
                id numObj = ((id (*)(id, SEL, id))objc_msgSend)(userInfo, sel_registerName("objectForKey:"), keyNum);
                const char *utf8_num = numObj ? (const char*)((const char* (*)(id, SEL))objc_msgSend)(numObj, sel_registerName("UTF8String")) : "";

                // Check for additionalAction (e.g. "answer_phone")
                id addAct = ((id (*)(id, SEL))objc_msgSend)(notification, sel_registerName("additionalActivationAction"));
                if (addAct) {
                    id ident = ((id (*)(id, SEL))objc_msgSend)(addAct, sel_registerName("identifier"));
                    const char *utf8_ident = ident ? (const char*)((const char* (*)(id, SEL))objc_msgSend)(ident, sel_registerName("UTF8String")) : "";
                    if (utf8_ident && strcmp(utf8_ident, "answer_phone") == 0) {
                        on_mac_call_notification_action("answer_phone", utf8_num);
                        return;
                    }
                }

                // If Action button ("Accept on Mac") or clicked body
                if (actType == 2 || actType == 1) {
                    on_mac_call_notification_action("accept_mac", utf8_num);
                }
                return;
            }
        }
    }

    // NSUserNotificationActivationTypeReplied == 3
    if (actType == 3) {
        id response = ((id (*)(id, SEL))objc_msgSend)(notification, sel_registerName("response"));
        if (response) {
            id str = ((id (*)(id, SEL))objc_msgSend)(response, sel_registerName("string"));
            id notifIdObj = nil;
            if (userInfo) {
                id key = ((id (*)(id, SEL, const char*))objc_msgSend)((id)nsStrClass, sel_registerName("stringWithUTF8String:"), "notif_id");
                notifIdObj = ((id (*)(id, SEL, id))objc_msgSend)(userInfo, sel_registerName("objectForKey:"), key);
            }
            const char *utf8_reply = str ? (const char*)((const char* (*)(id, SEL))objc_msgSend)(str, sel_registerName("UTF8String")) : "";
            const char *utf8_id = notifIdObj ? (const char*)((const char* (*)(id, SEL))objc_msgSend)(notifIdObj, sel_registerName("UTF8String")) : "";
            if (utf8_reply && utf8_id && strlen(utf8_reply) > 0) {
                on_mac_notification_reply(utf8_id, utf8_reply);
            }
        }
    }
}

static BOOL notif_center_should_present(id self, SEL _cmd, id center, id notification) {
    (void)self; (void)_cmd; (void)center; (void)notification;
    return YES;
}

static void ensure_notif_delegate(id center) {
    if (s_notif_delegate != nil) return;
    Class superCls = objc_getClass("NSObject");
    Class delCls = objc_allocateClassPair(superCls, "IdroidNotifDelegate", 0);
    if (delCls) {
        SEL didActSel = sel_registerName("userNotificationCenter:didActivateNotification:");
        class_addMethod(delCls, didActSel, (IMP)notif_center_did_activate, "v@:@@");
        SEL shouldPresSel = sel_registerName("userNotificationCenter:shouldPresentNotification:");
        class_addMethod(delCls, shouldPresSel, (IMP)notif_center_should_present, "c@:@@");
        objc_registerClassPair(delCls);
        s_notif_delegate = ((id (*)(id, SEL))objc_msgSend)(((id (*)(id, SEL))objc_msgSend)((id)delCls, sel_registerName("alloc")), sel_registerName("init"));
    }
    if (s_notif_delegate && center) {
        ((void (*)(id, SEL, id))objc_msgSend)(center, sel_registerName("setDelegate:"), s_notif_delegate);
    }
}

void post_mac_notification_full(const char *title, const char *subtitle, const char *message, int has_reply, const char *notif_id) {
    Class userNotifClass = objc_getClass("NSUserNotification");
    Class centerClass = objc_getClass("NSUserNotificationCenter");
    if (!userNotifClass || !centerClass) return;

    id center = ((id (*)(id, SEL))objc_msgSend)((id)centerClass, sel_registerName("defaultUserNotificationCenter"));
    if (center) {
        ensure_notif_delegate(center);
    }

    id notif = ((id (*)(id, SEL))objc_msgSend)((id)userNotifClass, sel_registerName("alloc"));
    notif = ((id (*)(id, SEL))objc_msgSend)(notif, sel_registerName("init"));

    Class nsStringClass = objc_getClass("NSString");
    id nsTitle = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), title ? title : "");
    id nsSub = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), subtitle ? subtitle : "");
    id nsMsg = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), message ? message : "");

    ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setTitle:"), nsTitle);
    ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setSubtitle:"), nsSub);
    ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setInformativeText:"), nsMsg);

    if (has_reply) {
        ((void (*)(id, SEL, BOOL))objc_msgSend)(notif, sel_registerName("setHasReplyButton:"), YES);
        id placeholder = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), "Reply...");
        ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setResponsePlaceholder:"), placeholder);

        if (notif_id) {
            Class dictClass = objc_getClass("NSDictionary");
            id nsId = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), notif_id);
            id nsKey = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), "notif_id");
            id dict = ((id (*)(id, SEL, id, id))objc_msgSend)((id)dictClass, sel_registerName("dictionaryWithObject:forKey:"), nsId, nsKey);
            ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setUserInfo:"), dict);
        }
    } else {
        ((void (*)(id, SEL, BOOL))objc_msgSend)(notif, sel_registerName("setHasReplyButton:"), NO);
    }

    if (center) {
        ((void (*)(id, SEL, id))objc_msgSend)(center, sel_registerName("deliverNotification:"), notif);
    }
}

void post_mac_notification(const char *title, const char *subtitle, const char *message) {
    post_mac_notification_full(title, subtitle, message, 0, NULL);
}

void post_mac_incoming_call_notification(const char *caller_name, const char *caller_number) {
    Class userNotifClass = objc_getClass("NSUserNotification");
    Class centerClass = objc_getClass("NSUserNotificationCenter");
    if (!userNotifClass || !centerClass) return;

    id center = ((id (*)(id, SEL))objc_msgSend)((id)centerClass, sel_registerName("defaultUserNotificationCenter"));
    if (center) {
        ensure_notif_delegate(center);
    }

    id notif = ((id (*)(id, SEL))objc_msgSend)((id)userNotifClass, sel_registerName("alloc"));
    notif = ((id (*)(id, SEL))objc_msgSend)(notif, sel_registerName("init"));

    Class nsStringClass = objc_getClass("NSString");
    id nsTitle = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), "Incoming Phone Call");
    id nsSub = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), caller_name ? caller_name : "Incoming Call");
    id nsMsg = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), caller_number ? caller_number : "Tap to answer on Mac");

    ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setTitle:"), nsTitle);
    ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setSubtitle:"), nsSub);
    ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setInformativeText:"), nsMsg);

    ((void (*)(id, SEL, BOOL))objc_msgSend)(notif, sel_registerName("setHasActionButton:"), YES);
    id actionTitle = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), "Accept on Mac");
    ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setActionButtonTitle:"), actionTitle);
    id otherTitle = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), "Decline");
    ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setOtherButtonTitle:"), otherTitle);

    // Additional action: "Answer on Phone"
    Class actionClass = objc_getClass("NSUserNotificationAction");
    if (actionClass) {
        id actId = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), "answer_phone");
        id actTitle = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), "Answer on Phone");
        id action = ((id (*)(id, SEL, id, id))objc_msgSend)((id)actionClass, sel_registerName("actionWithIdentifier:title:"), actId, actTitle);
        Class nsArrayClass = objc_getClass("NSArray");
        id arr = ((id (*)(id, SEL, id))objc_msgSend)((id)nsArrayClass, sel_registerName("arrayWithObject:"), action);
        ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setAdditionalActions:"), arr);
    }

    Class dictClass = objc_getClass("NSDictionary");
    id typeKey = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), "type");
    id typeVal = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), "call");
    id numKey = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), "number");
    id numVal = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), caller_number ? caller_number : "");
    id dict = ((id (*)(id, SEL, id, id, id, id, ...))objc_msgSend)((id)dictClass, sel_registerName("dictionaryWithObjectsAndKeys:"), typeVal, typeKey, numVal, numKey, nil);
    ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setUserInfo:"), dict);

    // Default ring sound
    id soundName = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), "NSUserNotificationDefaultSoundName");
    ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setSoundName:"), soundName);

    if (center) {
        ((void (*)(id, SEL, id))objc_msgSend)(center, sel_registerName("deliverNotification:"), notif);
    }
}

void cancel_mac_call_notifications(void) {
    Class centerClass = objc_getClass("NSUserNotificationCenter");
    if (!centerClass) return;
    id center = ((id (*)(id, SEL))objc_msgSend)((id)centerClass, sel_registerName("defaultUserNotificationCenter"));
    if (!center) return;
    id delivered = ((id (*)(id, SEL))objc_msgSend)(center, sel_registerName("deliveredNotifications"));
    if (!delivered) return;
    unsigned long count = ((unsigned long (*)(id, SEL))objc_msgSend)(delivered, sel_registerName("count"));
    Class nsStrClass = objc_getClass("NSString");
    id keyType = ((id (*)(id, SEL, const char*))objc_msgSend)((id)nsStrClass, sel_registerName("stringWithUTF8String:"), "type");
    for (unsigned long i = 0; i < count; i++) {
        id n = ((id (*)(id, SEL, unsigned long))objc_msgSend)(delivered, sel_registerName("objectAtIndex:"), i);
        id uinfo = ((id (*)(id, SEL))objc_msgSend)(n, sel_registerName("userInfo"));
        if (uinfo) {
            id t = ((id (*)(id, SEL, id))objc_msgSend)(uinfo, sel_registerName("objectForKey:"), keyType);
            if (t) {
                const char *s = (const char*)((const char* (*)(id, SEL))objc_msgSend)(t, sel_registerName("UTF8String"));
                if (s && strcmp(s, "call") == 0) {
                    ((void (*)(id, SEL, id))objc_msgSend)(center, sel_registerName("removeDeliveredNotification:"), n);
                }
            }
        }
    }
}

long get_mac_pasteboard_change_count(void) {
    Class pbClass = objc_getClass("NSPasteboard");
    if (!pbClass) return 0;
    id pb = ((id (*)(id, SEL))objc_msgSend)((id)pbClass, sel_registerName("generalPasteboard"));
    if (!pb) return 0;
    return ((long (*)(id, SEL))objc_msgSend)(pb, sel_registerName("changeCount"));
}

#include <unistd.h>

void mac_show_osd(int image_type, int percent) {
    CFURLRef url = CFURLCreateWithFileSystemPath(kCFAllocatorDefault, CFSTR("/System/Library/PrivateFrameworks/OSD.framework"), kCFURLPOSIXPathStyle, true);
    if (url) {
        CFBundleRef bundle = CFBundleCreate(kCFAllocatorDefault, url);
        if (bundle) CFBundleLoadExecutable(bundle);
        CFRelease(url);
    }

    Class osdClass = objc_getClass("OSDManager");
    if (!osdClass) return;

    SEL sharedSel = sel_registerName("sharedManager");
    id mgr = ((id (*)(id, SEL))objc_msgSend)((id)osdClass, sharedSel);
    if (!mgr) return;

    CGDirectDisplayID disp = CGMainDisplayID();
    if (percent < 0) percent = 0;
    if (percent > 100) percent = 100;
    unsigned int filled = (unsigned int)((percent * 16 + 50) / 100);

    SEL showSel = sel_registerName("showImage:onDisplayID:priority:msecUntilFade:filledChiclets:totalChiclets:locked:");
    ((void (*)(id, SEL, long long, unsigned int, unsigned int, unsigned int, unsigned int, unsigned int, BOOL))objc_msgSend)(
        mgr, showSel, (long long)image_type, (unsigned int)disp, 1, 1000, filled, 16, NO
    );
}

void mac_mouse_move(float dx, float dy) {
    CGEventRef current = CGEventCreate(NULL);
    if (!current) return;
    CGPoint pt = CGEventGetLocation(current);
    CFRelease(current);

    pt.x += (double)dx;
    pt.y += (double)dy;

    CGDirectDisplayID mainDisp = CGMainDisplayID();
    CGRect bounds = CGDisplayBounds(mainDisp);
    if (pt.x < bounds.origin.x) pt.x = bounds.origin.x;
    if (pt.x > bounds.origin.x + bounds.size.width - 1.0) pt.x = bounds.origin.x + bounds.size.width - 1.0;
    if (pt.y < bounds.origin.y) pt.y = bounds.origin.y;
    if (pt.y > bounds.origin.y + bounds.size.height - 1.0) pt.y = bounds.origin.y + bounds.size.height - 1.0;

    CGWarpMouseCursorPosition(pt);

    CGEventRef move = CGEventCreateMouseEvent(NULL, kCGEventMouseMoved, pt, kCGMouseButtonLeft);
    if (move) {
        CGEventPost(kCGSessionEventTap, move);
        CFRelease(move);
    }
}

void mac_mouse_click(int is_right) {
    CGEventRef current = CGEventCreate(NULL);
    if (!current) return;
    CGPoint pt = CGEventGetLocation(current);
    CFRelease(current);

    CGEventType downType = is_right ? kCGEventRightMouseDown : kCGEventLeftMouseDown;
    CGEventType upType = is_right ? kCGEventRightMouseUp : kCGEventLeftMouseUp;
    CGMouseButton btn = is_right ? kCGMouseButtonRight : kCGMouseButtonLeft;

    CGEventRef down = CGEventCreateMouseEvent(NULL, downType, pt, btn);
    if (down) {
        CGEventPost(kCGSessionEventTap, down);
        CFRelease(down);
    }
    usleep(10000);
    CGEventRef up = CGEventCreateMouseEvent(NULL, upType, pt, btn);
    if (up) {
        CGEventPost(kCGSessionEventTap, up);
        CFRelease(up);
    }
}

void mac_mouse_scroll(int dy) {
    CGEventRef scroll = CGEventCreateScrollWheelEvent(NULL, kCGScrollEventUnitLine, 1, dy);
    if (scroll) {
        CGEventPost(kCGSessionEventTap, scroll);
        CFRelease(scroll);
    }
}

void mac_type_text(const char *utf8_str) {
    if (!utf8_str) return;
    CFStringRef cfStr = CFStringCreateWithCString(kCFAllocatorDefault, utf8_str, kCFStringEncodingUTF8);
    if (!cfStr) return;
    CFIndex len = CFStringGetLength(cfStr);
    if (len == 0) {
        CFRelease(cfStr);
        return;
    }
    UniChar *buffer = (UniChar *)malloc(sizeof(UniChar) * len);
    if (!buffer) {
        CFRelease(cfStr);
        return;
    }
    CFStringGetCharacters(cfStr, CFRangeMake(0, len), buffer);

    CGEventSourceRef src = CGEventSourceCreate(kCGEventSourceStateHIDSystemState);
    CGEventRef eventDown = CGEventCreateKeyboardEvent(src, 0, true);
    CGEventKeyboardSetUnicodeString(eventDown, len, buffer);
    CGEventPost(kCGSessionEventTap, eventDown);
    CFRelease(eventDown);

    CGEventRef eventUp = CGEventCreateKeyboardEvent(src, 0, false);
    CGEventPost(kCGSessionEventTap, eventUp);
    CFRelease(eventUp);

    if (src) CFRelease(src);
    free(buffer);
    CFRelease(cfStr);
}

void mac_press_key(const char *key_name) {
    if (!key_name) return;
    CGKeyCode code = 0;
    if (strcmp(key_name, "return") == 0 || strcmp(key_name, "enter") == 0) {
        code = 36;
    } else if (strcmp(key_name, "backspace") == 0 || strcmp(key_name, "delete") == 0) {
        code = 51;
    } else if (strcmp(key_name, "escape") == 0 || strcmp(key_name, "esc") == 0) {
        code = 53;
    } else if (strcmp(key_name, "space") == 0) {
        code = 49;
    } else if (strcmp(key_name, "tab") == 0) {
        code = 48;
    } else if (strcmp(key_name, "up") == 0) {
        code = 126;
    } else if (strcmp(key_name, "down") == 0) {
        code = 125;
    } else if (strcmp(key_name, "left") == 0) {
        code = 123;
    } else if (strcmp(key_name, "right") == 0) {
        code = 124;
    } else if (strcmp(key_name, "like") == 0 || strcmp(key_name, "l") == 0 || strcmp(key_name, "L") == 0) {
        code = 37; // kVK_ANSI_L - triggers Like on Instagram Reels and web media
    } else if (strcmp(key_name, "k") == 0 || strcmp(key_name, "K") == 0) {
        code = 40; // kVK_ANSI_K - play/pause on YouTube Shorts
    } else if (strcmp(key_name, "m") == 0 || strcmp(key_name, "M") == 0) {
        code = 46; // kVK_ANSI_M - mute/unmute
    } else {
        return;
    }

    CGEventSourceRef src = CGEventSourceCreate(kCGEventSourceStateHIDSystemState);
    if (!src) return;

    CGEventRef down = CGEventCreateKeyboardEvent(src, code, true);
    CGEventPost(kCGHIDEventTap, down);
    CGEventPost(kCGSessionEventTap, down);
    CFRelease(down);

    usleep(20000);

    CGEventRef up = CGEventCreateKeyboardEvent(src, code, false);
    CGEventPost(kCGHIDEventTap, up);
    CGEventPost(kCGSessionEventTap, up);
    CFRelease(up);
    CFRelease(src);
}

void mac_toggle_spotlight(void) {
    CGEventSourceRef src = CGEventSourceCreate(kCGEventSourceStateHIDSystemState);
    if (!src) return;

    // 1. Command down (keycode 55)
    CGEventRef cmdDown = CGEventCreateKeyboardEvent(src, 55, true);
    CGEventSetFlags(cmdDown, kCGEventFlagMaskCommand);
    CGEventPost(kCGHIDEventTap, cmdDown);
    CFRelease(cmdDown);

    usleep(15000);

    // 2. Space down (keycode 49) with Command flag
    CGEventRef spaceDown = CGEventCreateKeyboardEvent(src, 49, true);
    CGEventSetFlags(spaceDown, kCGEventFlagMaskCommand);
    CGEventPost(kCGHIDEventTap, spaceDown);
    CFRelease(spaceDown);

    usleep(30000);

    // 3. Space up
    CGEventRef spaceUp = CGEventCreateKeyboardEvent(src, 49, false);
    CGEventSetFlags(spaceUp, kCGEventFlagMaskCommand);
    CGEventPost(kCGHIDEventTap, spaceUp);
    CFRelease(spaceUp);

    usleep(15000);

    // 4. Command up
    CGEventRef cmdUp = CGEventCreateKeyboardEvent(src, 55, false);
    CGEventPost(kCGHIDEventTap, cmdUp);
    CFRelease(cmdUp);

    CFRelease(src);
}

int mac_is_location_services_enabled() {
    Class clManager = objc_getClass("CLLocationManager");
    if (!clManager) {
        CFURLRef url = CFURLCreateWithFileSystemPath(kCFAllocatorDefault, CFSTR("/System/Library/Frameworks/CoreLocation.framework"), kCFURLPOSIXPathStyle, true);
        if (url) {
            CFBundleRef bundle = CFBundleCreate(kCFAllocatorDefault, url);
            if (bundle) {
                CFBundleLoadExecutable(bundle);
            }
            CFRelease(url);
        }
        clManager = objc_getClass("CLLocationManager");
    }
    if (clManager) {
        SEL sel = sel_registerName("locationServicesEnabled");
        if (class_respondsToSelector(object_getClass((id)clManager), sel)) {
            BOOL (*func)(id, SEL) = (BOOL (*)(id, SEL))objc_msgSend;
            return func((id)clManager, sel) ? 1 : 0;
        }
    }
    return 0;
}

#include <dlfcn.h>
typedef Boolean (*MRSendCommandFunc)(unsigned int, void *);
static MRSendCommandFunc g_mr_send = NULL;
static int g_mr_attempted = 0;

int mac_media_remote_send(unsigned int cmd) {
    if (!g_mr_send && !g_mr_attempted) {
        g_mr_attempted = 1;
        void *handle = dlopen("/System/Library/PrivateFrameworks/MediaRemote.framework/MediaRemote", RTLD_LAZY);
        if (handle) {
            g_mr_send = (MRSendCommandFunc)dlsym(handle, "MRMediaRemoteSendCommand");
        }
    }
    if (g_mr_send) {
        return g_mr_send(cmd, NULL) ? 1 : 0;
    }
    return 0;
}

void post_mac_hardware_media_key(int key) {
    Class nsEventClass = objc_getClass("NSEvent");
    if (!nsEventClass) return;

    SEL selOtherEvent = sel_registerName("otherEventWithType:location:modifierFlags:timestamp:windowNumber:context:subtype:data1:data2:");
    SEL selCGEvent = sel_registerName("CGEvent");

    // Key down (downFlag = 0xa00)
    int downFlag = 0xa00;
    long data1Down = ((long)key << 16) | downFlag;
    id downEvent = ((id (*)(id, SEL, unsigned long, CGPoint, unsigned long, double, long, id, short, long, long))objc_msgSend)(
        (id)nsEventClass, selOtherEvent,
        14, // NSEventTypeSystemDefined
        CGPointZero,
        (unsigned long)downFlag,
        0.0,
        0,
        nil,
        8, // subtype
        data1Down,
        -1
    );
    if (downEvent) {
        CGEventRef cgDown = ((CGEventRef (*)(id, SEL))objc_msgSend)(downEvent, selCGEvent);
        if (cgDown) {
            CGEventPost(kCGHIDEventTap, cgDown);
        }
    }

    usleep(15000);

    // Key up (upFlag = 0xb00)
    int upFlag = 0xb00;
    long data1Up = ((long)key << 16) | upFlag;
    id upEvent = ((id (*)(id, SEL, unsigned long, CGPoint, unsigned long, double, long, id, short, long, long))objc_msgSend)(
        (id)nsEventClass, selOtherEvent,
        14, // NSEventTypeSystemDefined
        CGPointZero,
        (unsigned long)upFlag,
        0.0,
        0,
        nil,
        8, // subtype
        data1Up,
        -1
    );
    if (upEvent) {
        CGEventRef cgUp = ((CGEventRef (*)(id, SEL))objc_msgSend)(upEvent, selCGEvent);
        if (cgUp) {
            CGEventPost(kCGHIDEventTap, cgUp);
        }
    }
}

void set_mac_dock_icon_from_png(const char *png_path) {
    if (!png_path) return;
    char *path_copy = strdup(png_path);
    if (!path_copy) return;

    dispatch_async(dispatch_get_main_queue(), ^{
        Class nsImageClass = objc_getClass("NSImage");
        Class nsStringClass = objc_getClass("NSString");
        SEL selStringWithUTF8String = sel_registerName("stringWithUTF8String:");
        id nsPath = ((id (*)(id, SEL, const char *))objc_msgSend)(
            (id)nsStringClass, selStringWithUTF8String, path_copy
        );
        SEL selAlloc = sel_registerName("alloc");
        SEL selInitWithContentsOfFile = sel_registerName("initWithContentsOfFile:");
        id allocatedImage = ((id (*)(id, SEL))objc_msgSend)((id)nsImageClass, selAlloc);
        id img = ((id (*)(id, SEL, id))objc_msgSend)(allocatedImage, selInitWithContentsOfFile, nsPath);
        if (img) {
            Class nsAppClass = objc_getClass("NSApplication");
            SEL selSharedApp = sel_registerName("sharedApplication");
            id app = ((id (*)(id, SEL))objc_msgSend)((id)nsAppClass, selSharedApp);
            SEL selSetAppIcon = sel_registerName("setApplicationIconImage:");
            ((void (*)(id, SEL, id))objc_msgSend)(app, selSetAppIcon, img);
        }
        free(path_copy);
    });
}

int get_mac_pasteboard_image_png(unsigned char **out_data, size_t *out_len) {
    if (!out_data || !out_len) return 0;
    *out_data = NULL;
    *out_len = 0;

    Class pbClass = objc_getClass("NSPasteboard");
    if (!pbClass) return 0;
    id pb = ((id (*)(id, SEL))objc_msgSend)((id)pbClass, sel_registerName("generalPasteboard"));
    if (!pb) return 0;

    Class nsStringClass = objc_getClass("NSString");
    id pngType = ((id (*)(id, SEL, const char *))objc_msgSend)(
        (id)nsStringClass, sel_registerName("stringWithUTF8String:"), "public.png"
    );
    id tiffType = ((id (*)(id, SEL, const char *))objc_msgSend)(
        (id)nsStringClass, sel_registerName("stringWithUTF8String:"), "public.tiff"
    );

    id data = ((id (*)(id, SEL, id))objc_msgSend)(pb, sel_registerName("dataForType:"), pngType);
    if (!data) {
        // Try reading NSImage object directly from pasteboard
        Class nsImageClass = objc_getClass("NSImage");
        Class nsArrayClass = objc_getClass("NSArray");
        if (nsImageClass && nsArrayClass) {
            id classes = ((id (*)(id, SEL, id, ...))objc_msgSend)((id)nsArrayClass, sel_registerName("arrayWithObject:"), (id)nsImageClass);
            id objects = ((id (*)(id, SEL, id, id))objc_msgSend)(pb, sel_registerName("readObjectsForClasses:options:"), classes, NULL);
            if (objects) {
                unsigned long count = (unsigned long)((unsigned long (*)(id, SEL))objc_msgSend)(objects, sel_registerName("count"));
                if (count > 0) {
                    id img = ((id (*)(id, SEL, unsigned long))objc_msgSend)(objects, sel_registerName("objectAtIndex:"), 0);
                    if (img) {
                        id tiffData = ((id (*)(id, SEL))objc_msgSend)(img, sel_registerName("TIFFRepresentation"));
                        if (tiffData) {
                            Class repClass = objc_getClass("NSBitmapImageRep");
                            id rep = ((id (*)(id, SEL, id))objc_msgSend)((id)repClass, sel_registerName("imageRepWithData:"), tiffData);
                            if (rep) {
                                data = ((id (*)(id, SEL, unsigned long, id))objc_msgSend)(
                                    rep, sel_registerName("representationUsingType:properties:"), 4, NULL
                                );
                            }
                        }
                    }
                }
            }
        }
    }
    if (!data) {
        id tiffData = ((id (*)(id, SEL, id))objc_msgSend)(pb, sel_registerName("dataForType:"), tiffType);
        if (tiffData) {
            Class repClass = objc_getClass("NSBitmapImageRep");
            id rep = ((id (*)(id, SEL, id))objc_msgSend)((id)repClass, sel_registerName("imageRepWithData:"), tiffData);
            if (rep) {
                // NSBitmapImageFileTypePNG = 4
                data = ((id (*)(id, SEL, unsigned long, id))objc_msgSend)(
                    rep, sel_registerName("representationUsingType:properties:"), 4, NULL
                );
            }
        }
    }

    if (data) {
        size_t len = (size_t)((unsigned long (*)(id, SEL))objc_msgSend)(data, sel_registerName("length"));
        const void *bytes = ((const void * (*)(id, SEL))objc_msgSend)(data, sel_registerName("bytes"));
        if (len > 0 && bytes) {
            unsigned char *buf = (unsigned char *)malloc(len);
            if (buf) {
                memcpy(buf, bytes, len);
                *out_data = buf;
                *out_len = len;
                return 1;
            }
        }
    }
    return 0;
}

void free_mac_buffer(unsigned char *ptr) {
    if (ptr) free(ptr);
}

void set_mac_pasteboard_image_png(const unsigned char *data, size_t len) {
    if (!data || len == 0) return;
    Class pbClass = objc_getClass("NSPasteboard");
    if (!pbClass) return;
    id pb = ((id (*)(id, SEL))objc_msgSend)((id)pbClass, sel_registerName("generalPasteboard"));
    if (!pb) return;

    Class nsDataClass = objc_getClass("NSData");
    id nsData = ((id (*)(id, SEL, const void *, unsigned long))objc_msgSend)(
        (id)nsDataClass, sel_registerName("dataWithBytes:length:"), data, (unsigned long)len
    );
    if (!nsData) return;

    Class nsImageClass = objc_getClass("NSImage");
    id allocatedImage = ((id (*)(id, SEL))objc_msgSend)((id)nsImageClass, sel_registerName("alloc"));
    id img = ((id (*)(id, SEL, id))objc_msgSend)(allocatedImage, sel_registerName("initWithData:"), nsData);

    ((void (*)(id, SEL))objc_msgSend)(pb, sel_registerName("clearContents"));

    // Write NSImage object using writeObjects: so all Cocoa apps (Chrome, Notes, Slack, Photoshop, etc.) recognize it
    if (img) {
        Class nsArrayClass = objc_getClass("NSArray");
        if (nsArrayClass) {
            id imgArray = ((id (*)(id, SEL, id, ...))objc_msgSend)((id)nsArrayClass, sel_registerName("arrayWithObject:"), img);
            ((BOOL (*)(id, SEL, id))objc_msgSend)(pb, sel_registerName("writeObjects:"), imgArray);
        }
    }

    // Also write raw PNG data representation for apps querying public.png
    Class nsStringClass = objc_getClass("NSString");
    id pngType = ((id (*)(id, SEL, const char *))objc_msgSend)(
        (id)nsStringClass, sel_registerName("stringWithUTF8String:"), "public.png"
    );
    ((void (*)(id, SEL, id, id))objc_msgSend)(pb, sel_registerName("setData:forType:"), nsData, pngType);
}
