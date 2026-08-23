require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "rivium-push-react-native"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => "13.0" }
  s.source       = { :git => "https://github.com/rivium/rivium-push-react-native.git", :tag => "#{s.version}" }

  # Only include the React Native bridge files - native SDK comes from dependency
  s.source_files = "ios/RiviumPushReactNative.{m,swift}"

  s.dependency "React-Core"

  # Use the native iOS SDK as a dependency (like Android uses AAR from Maven)
  # During development, you can use a local path:
  #   pod 'RiviumPush', :path => '../../../../ios/RiviumPush'
  # For production, use the published version from a spec repo or direct URL
  s.dependency "RiviumPushSDK", "~> 0.1.7"

  s.swift_version = "5.0"
end
