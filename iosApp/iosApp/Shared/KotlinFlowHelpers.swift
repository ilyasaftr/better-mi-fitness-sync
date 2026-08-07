import Foundation
import ComposeApp

/// Holds a Kotlin FlowWatcher so SwiftUI can cancel on disappear.
final class FlowSubscription {
    private var watcher: FlowWatcher?

    init(_ watcher: FlowWatcher?) {
        self.watcher = watcher
    }

    func cancel() {
        watcher?.close()
        watcher = nil
    }

    deinit {
        cancel()
    }
}

/// SF Symbol names for Mi metric keys.
enum MetricSymbol {
    static func name(for key: String) -> String {
        switch key {
        case "heart_rate", "hrv": return "heart.fill"
        case "resting_heart_rate": return "heart.circle"
        case "sleep": return "bed.double.fill"
        case "steps": return "figure.walk"
        case "distance": return "ruler"
        case "active_calories": return "flame.fill"
        case "spo2", "blood_pressure": return "drop.fill"
        case "weight": return "scalemass.fill"
        case "workouts", "vo2_max": return "figure.run"
        case "temperature": return "thermometer.medium"
        default: return "circle.grid.2x2"
        }
    }
}
