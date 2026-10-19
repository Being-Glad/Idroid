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

        if (strcasestr(owner_buf, "scrcpy") != NULL || strcasestr(title_buf, "Idroid") != NULL) {
            CFDictionaryRef bounds = (CFDictionaryRef)CFDictionaryGetValue(dict, kCGWindowBounds);
            if (bounds) {
                CGRect r;
                if (CGRectMakeWithDictionaryRepresentation(bounds, &r)) {
                    if (r.size.width >= 200.0 && r.size.height >= 300.0) {
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

void post_mac_notification(const char *title, const char *subtitle, const char *message) {
    Class userNotifClass = objc_getClass("NSUserNotification");
    Class centerClass = objc_getClass("NSUserNotificationCenter");
    if (!userNotifClass || !centerClass) return;

    id notif = ((id (*)(id, SEL))objc_msgSend)((id)userNotifClass, sel_registerName("alloc"));
    notif = ((id (*)(id, SEL))objc_msgSend)(notif, sel_registerName("init"));

    Class nsStringClass = objc_getClass("NSString");
    id nsTitle = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), title ? title : "");
    id nsSub = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), subtitle ? subtitle : "");
    id nsMsg = ((id (*)(id, SEL, const char *))objc_msgSend)((id)nsStringClass, sel_registerName("stringWithUTF8String:"), message ? message : "");

    ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setTitle:"), nsTitle);
    ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setSubtitle:"), nsSub);
    ((void (*)(id, SEL, id))objc_msgSend)(notif, sel_registerName("setInformativeText:"), nsMsg);

    id center = ((id (*)(id, SEL))objc_msgSend)((id)centerClass, sel_registerName("defaultUserNotificationCenter"));
    if (center) {
        ((void (*)(id, SEL, id))objc_msgSend)(center, sel_registerName("deliverNotification:"), notif);
    }
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
    } else {
        return;
    }

    CGEventSourceRef src = CGEventSourceCreate(kCGEventSourceStateHIDSystemState);
    CGEventRef down = CGEventCreateKeyboardEvent(src, code, true);
    CGEventPost(kCGSessionEventTap, down);
    CFRelease(down);

    usleep(15000);

    CGEventRef up = CGEventCreateKeyboardEvent(src, code, false);
    CGEventPost(kCGSessionEventTap, up);
    CFRelease(up);
    if (src) CFRelease(src);
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


