import SwiftUI

public struct ServicesView: View {
    @Environment(\.dismiss) private var dismiss

    public init() {}

    public var body: some View {
        ZStack {
            Color(hex: "12151A").ignoresSafeArea()

            VStack {
                HStack {
                    Button(action: { dismiss() }) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                            Text("Back")
                        }
                        .foregroundColor(.cyan)
                    }
                    Spacer()
                    Text("Service Procedures")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color(hex: "1A202C"))

                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "wrench.and.screwdriver.fill")
                        .font(.system(size: 44))
                        .foregroundColor(.purple)
                    Text("Safe Service Routines")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    Text("Module adaptation resets, camera calibration, and reboot procedures will be unlocked in Phase 4.")
                        .font(.system(size: 13))
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
                Spacer()
            }
        }
        .navigationBarHidden(true)
    }
}
