import Foundation
import HealthKit

/// Native Swift bridge for HealthKit operations.
/// This provides a cleaner Swift-native interface that the Kotlin/Native
/// interop can call through for complex HealthKit operations.
@objc public class HealthKitBridge: NSObject {
    private let healthStore = HKHealthStore()

    @objc public static let shared = HealthKitBridge()

    private override init() {
        super.init()
    }

    @objc public var isAvailable: Bool {
        return HKHealthStore.isHealthDataAvailable()
    }

    @objc public func requestAuthorization(completion: @escaping (Bool, Error?) -> Void) {
        guard HKHealthStore.isHealthDataAvailable() else {
            completion(false, NSError(domain: "HealthKit", code: -1,
                                     userInfo: [NSLocalizedDescriptionKey: "HealthKit not available"]))
            return
        }

        // Sample types only. Do NOT include HKCorrelationType.bloodPressure — HealthKit
        // throws NSException (_throwIfAuthorizationDisallowedForSharing) and aborts the app.
        // Blood pressure is authorized via systolic + diastolic quantity types.
        var typesToShare = Set<HKSampleType>()
        let ids: [HKQuantityTypeIdentifier] = [
            .heartRate, .restingHeartRate, .stepCount, .distanceWalkingRunning,
            .activeEnergyBurned, .oxygenSaturation, .bodyMass, .bodyFatPercentage,
            .bodyTemperature, .bloodPressureSystolic, .bloodPressureDiastolic, .vo2Max,
        ]
        for id in ids {
            if let t = HKQuantityType.quantityType(forIdentifier: id) { typesToShare.insert(t) }
        }
        if let sleep = HKCategoryType.categoryType(forIdentifier: .sleepAnalysis) {
            typesToShare.insert(sleep)
        }
        typesToShare.insert(HKObjectType.workoutType())

        healthStore.requestAuthorization(toShare: typesToShare, read: []) { success, error in
            completion(success, error)
        }
    }

    @objc public func saveHeartRateSamples(timestamps: [Double], bpmValues: [Double],
                                            completion: @escaping (Bool, Error?) -> Void) {
        guard let hrType = HKQuantityType.quantityType(forIdentifier: .heartRate) else {
            completion(false, NSError(domain: "HealthKit", code: -1,
                                     userInfo: [NSLocalizedDescriptionKey: "HR type unavailable"]))
            return
        }

        let unit = HKUnit.count().unitDivided(by: .minute())
        var samples: [HKQuantitySample] = []

        for i in 0..<timestamps.count {
            let date = Date(timeIntervalSince1970: timestamps[i])
            let quantity = HKQuantity(unit: unit, doubleValue: bpmValues[i])
            let sample = HKQuantitySample(type: hrType, quantity: quantity,
                                          start: date, end: date)
            samples.append(sample)
        }

        healthStore.save(samples) { success, error in
            completion(success, error)
        }
    }

    @objc public func saveSteps(startTimestamp: Double, endTimestamp: Double, count: Double,
                                 completion: @escaping (Bool, Error?) -> Void) {
        guard let stepsType = HKQuantityType.quantityType(forIdentifier: .stepCount) else {
            completion(false, nil)
            return
        }

        let start = Date(timeIntervalSince1970: startTimestamp)
        let end = Date(timeIntervalSince1970: endTimestamp)
        let quantity = HKQuantity(unit: .count(), doubleValue: count)
        let sample = HKQuantitySample(type: stepsType, quantity: quantity, start: start, end: end)

        healthStore.save(sample) { success, error in
            completion(success, error)
        }
    }

    @objc public func saveSpO2(timestamp: Double, percentage: Double,
                                completion: @escaping (Bool, Error?) -> Void) {
        guard let spo2Type = HKQuantityType.quantityType(forIdentifier: .oxygenSaturation) else {
            completion(false, nil)
            return
        }

        let date = Date(timeIntervalSince1970: timestamp)
        let quantity = HKQuantity(unit: .percent(), doubleValue: percentage / 100.0)
        let sample = HKQuantitySample(type: spo2Type, quantity: quantity, start: date, end: date)

        healthStore.save(sample) { success, error in
            completion(success, error)
        }
    }

    @objc public func saveSleepSession(startTimestamp: Double, endTimestamp: Double, value: Int,
                                        completion: @escaping (Bool, Error?) -> Void) {
        guard let sleepType = HKCategoryType.categoryType(forIdentifier: .sleepAnalysis) else {
            completion(false, nil)
            return
        }

        let start = Date(timeIntervalSince1970: startTimestamp)
        let end = Date(timeIntervalSince1970: endTimestamp)

        let hkValue: HKCategoryValueSleepAnalysis
        switch value {
        case 1: hkValue = .awake
        case 2: hkValue = .asleepCore
        case 3: hkValue = .asleepDeep
        case 4: hkValue = .asleepREM
        default: hkValue = .asleepCore
        }

        let sample = HKCategorySample(type: sleepType, value: hkValue.rawValue,
                                       start: start, end: end)

        healthStore.save(sample) { success, error in
            completion(success, error)
        }
    }

    @objc public func saveWorkout(startTimestamp: Double, endTimestamp: Double, activityType: UInt,
                                   calories: Double, distance: Double,
                                   completion: @escaping (Bool, Error?) -> Void) {
        let start = Date(timeIntervalSince1970: startTimestamp)
        let end = Date(timeIntervalSince1970: endTimestamp)
        let type = HKWorkoutActivityType(rawValue: activityType) ?? .other

        let workout = HKWorkout(activityType: type,
                                start: start,
                                end: end,
                                duration: endTimestamp - startTimestamp,
                                totalEnergyBurned: HKQuantity(unit: .kilocalorie(), doubleValue: calories),
                                totalDistance: HKQuantity(unit: .meter(), doubleValue: distance),
                                metadata: nil)

        healthStore.save(workout) { success, error in
            completion(success, error)
        }
    }
}
