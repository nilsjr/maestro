## Build the UI test
## TODO: make destination generic for iOS 15 simulator
xcodebuild -project ./maestro-driver-ios/maestro-driver-ios.xcodeproj \
  -scheme maestro-driver-ios -sdk iphonesimulator \
  -destination "platform=iOS Simulator,name=iPhone 13,OS=15.4" \
  -IDEBuildLocationStyle=Custom \
  -IDECustomBuildLocationType=Absolute \
  -IDECustomBuildProductsPath="$PWD/build/Products" \
  build-for-testing || exit 1

## Remove intermediates, output and copy runner in maestro-xcuitest-driver
mv "$PWD"/build/Products/Debug-iphonesimulator/maestro-driver-iosUITests-Runner.app ./maestro-xcuitest-driver/src/main/resources/maestro-driver-iosUITests-Runner.app || exit 1

mv "$PWD"/build/Products/Debug-iphonesimulator/maestro-driver-ios.app ./maestro-xcuitest-driver/src/main/resources/maestro-driver-ios.app || exit 1

mv "$PWD"/build/Products/*.xctestrun ./maestro-xcuitest-driver/src/main/resources/maestro-driver-ios-config.xctestrun || exit 1

(cd ./maestro-xcuitest-driver/src/main/resources && zip -r maestro-driver-iosUITests-Runner.zip ./maestro-driver-iosUITests-Runner.app) || exit 1
(cd ./maestro-xcuitest-driver/src/main/resources && zip -r maestro-driver-ios.zip ./maestro-driver-ios.app) || exit 1
rm -r ./maestro-xcuitest-driver/src/main/resources/*.app || exit 1
