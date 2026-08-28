import SwiftUI

/// Native SwiftUI settings.
///
/// Deliberately native rather than Compose so it inherits iOS behavior for free:
/// dynamic type, VoiceOver, keyboard focus, `SecureField`, and the system `Form` chrome.
/// The Android build used only Material icons here (a gear and a back arrow), so replacing them
/// with SF Symbols costs no visual identity.
struct SettingsView: View {
  @EnvironmentObject private var state: SharedStateBridge
  @Environment(\.dismiss) private var dismiss

  var body: some View {
    NavigationStack {
      Form {
        engineSection
        modelSection
        aboutSection
      }
      .navigationTitle("设置")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .navigationBarLeading) {
          Button {
            dismiss()
          } label: {
            Image(systemName: "chevron.left")
              .accessibilityLabel("返回")
          }
        }
      }
    }
  }

  // MARK: - AI engine

  private var engineSection: some View {
    Section {
      Picker("推理引擎", selection: $state.engineType) {
        Text("OpenAI 兼容").tag("OPENAI_COMPATIBLE")
        Text("OpenAI Responses").tag("OPENAI_RESPONSES")
        Text("Anthropic").tag("ANTHROPIC")
        Text("Gemini").tag("GEMINI")
        Text("本地模型").tag("LOCAL_MODEL")
      }

      TextField("Endpoint", text: $state.baseUrl)
        .keyboardType(.URL)
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()

      TextField("模型名称", text: $state.modelName)
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()

      // SecureField keeps the key out of screenshots and the keyboard cache.
      SecureField("API Key", text: $state.apiKey)
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
    } header: {
      Text("AI 推理引擎配置")
    } footer: {
      Text("远程引擎需自行配置 Endpoint、模型与 API Key。API Key 仅保存在本机。")
    }
  }

  // MARK: - Local model

  private var modelSection: some View {
    Section {
      Picker("推理后端", selection: $state.backendPreference) {
        Text("NPU").tag("NPU")
        Text("GPU").tag("GPU")
        Text("CPU").tag("CPU")
      }

      HStack {
        Text("模型状态")
        Spacer()
        Text(state.localModelStatusText)
          .foregroundStyle(.secondary)
      }

      if state.isLocalModelDownloading {
        ProgressView(value: state.downloadProgress) {
          Text("下载中")
        }
      }

      Button {
        state.startModel()
      } label: {
        Label("启动模型", systemImage: "play.fill")
      }
      .disabled(!state.canStartModel)

      Button(role: .destructive) {
        state.stopModel()
      } label: {
        Label("停止模型", systemImage: "stop.fill")
      }
      .disabled(!state.canStopModel)
    } header: {
      Text("本地模型")
    } footer: {
      Text("本地推理使用 LiteRT-LM，需先下载 Gemma 4 E4B 模型（约 3.7 GB）。")
    }
  }

  // MARK: - About

  private var aboutSection: some View {
    Section {
      HStack {
        Text("版本")
        Spacer()
        Text(state.appVersion)
          .foregroundStyle(.secondary)
      }
      Link("源代码许可（GPLv3）", destination: URL(string: "https://www.gnu.org/licenses/gpl-3.0.html")!)
    } header: {
      Text("关于")
    }
  }
}

#Preview {
  SettingsView()
    .environmentObject(SharedStateBridge.shared)
}
