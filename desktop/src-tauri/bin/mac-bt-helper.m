#import <Foundation/Foundation.h>
#import <IOBluetooth/IOBluetooth.h>

int main(int argc, const char * argv[]) {
    @autoreleasepool {
        if (argc < 2 || strcmp(argv[1], "list") == 0) {
            NSArray *paired = [IOBluetoothDevice pairedDevices];
            NSMutableArray *list = [NSMutableArray array];
            for (IOBluetoothDevice *dev in paired) {
                NSString *name = [dev nameOrAddress] ?: @"Unknown Device";
                NSString *addr = [dev addressString] ?: @"";
                BOOL connected = [dev isConnected];
                [list addObject:@{
                    @"name": name,
                    @"address": addr,
                    @"connected": @(connected)
                }];
            }
            NSData *data = [NSJSONSerialization dataWithJSONObject:list options:0 error:nil];
            if (data) {
                printf("%s\n", [[[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] UTF8String]);
            } else {
                printf("[]\n");
            }
            return 0;
        }

        const char *cmd = argv[1];
        if (strcmp(cmd, "connect") == 0 && argc >= 3) {
            NSString *addr = [NSString stringWithUTF8String:argv[2]];
            IOBluetoothDevice *dev = [IOBluetoothDevice deviceWithAddressString:addr];
            if (!dev) {
                printf("{\"success\":false,\"error\":\"Device not found with address %s\"}\n", [addr UTF8String]);
                return 1;
            }
            IOReturn ret = [dev openConnection];
            BOOL success = (ret == kIOReturnSuccess);
            printf("{\"success\":%s,\"ret\":%d,\"name\":\"%s\"}\n", 
                   success ? "true" : "false", 
                   ret, 
                   [[dev nameOrAddress] UTF8String] ?: "");
            return success ? 0 : 1;
        } else if (strcmp(cmd, "disconnect") == 0 && argc >= 3) {
            NSString *addr = [NSString stringWithUTF8String:argv[2]];
            IOBluetoothDevice *dev = [IOBluetoothDevice deviceWithAddressString:addr];
            if (!dev) {
                printf("{\"success\":false,\"error\":\"Device not found with address %s\"}\n", [addr UTF8String]);
                return 1;
            }
            IOReturn ret = [dev closeConnection];
            BOOL success = (ret == kIOReturnSuccess);
            printf("{\"success\":%s,\"ret\":%d,\"name\":\"%s\"}\n", 
                   success ? "true" : "false", 
                   ret, 
                   [[dev nameOrAddress] UTF8String] ?: "");
            return success ? 0 : 1;
        } else {
            printf("{\"success\":false,\"error\":\"Unknown command\"}\n");
            return 1;
        }
    }
    return 0;
}
