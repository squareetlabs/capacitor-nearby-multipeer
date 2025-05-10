import Foundation

@objc public class NearbyMultipeer: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
