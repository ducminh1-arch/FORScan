import SwiftUI

public struct DemoChip: View {
    public init() {}

    public var body: some View {
        Text("DEMO")
            .font(.system(size: 10, weight: .bold, design: .monospaced))
            .foregroundColor(Color(red: 1.0, green: 0.7, blue: 0.0)) // Amber #FFB300
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(
                RoundedRectangle(cornerRadius: 5)
                    .fill(Color(red: 1.0, green: 0.7, blue: 0.0).opacity(0.2))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 5)
                    .stroke(Color(red: 1.0, green: 0.7, blue: 0.0), lineWidth: 1)
            )
    }
}

