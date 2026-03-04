// Modules/ARLauncherModule.m
// Objective-C bridge — exposes ARLauncher to React Native JS

#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(ARLauncher, NSObject)

RCT_EXTERN_METHOD(openARActivity:(NSString *)initialModelPath
                  modelPathsJson:(NSString *)modelPathsJson
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

+ (BOOL)requiresMainQueueSetup;

@end
