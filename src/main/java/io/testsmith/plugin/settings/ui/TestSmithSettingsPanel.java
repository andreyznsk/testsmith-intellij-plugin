package io.testsmith.plugin.settings.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import io.testsmith.plugin.settings.BuildToolMode;
import io.testsmith.plugin.settings.ExecutionMode;
import io.testsmith.plugin.settings.LlmProvider;
import io.testsmith.plugin.settings.TestSmithProjectSettings;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class TestSmithSettingsPanel implements Disposable {
    private final Disposable disposable = Disposer.newDisposable("TestSmithSettingsPanel");
    private final JPanel root;

    private final ComboBox<BuildToolMode> buildToolModeCombo = new ComboBox<>(BuildToolMode.values());
    private final ComboBox<ExecutionMode> executionModeCombo = new ComboBox<>(ExecutionMode.values());
    private final JSpinner maxIterationsSpinner = new JSpinner(new SpinnerNumberModel(10, 1, Integer.MAX_VALUE, 1));
    private final JPanel autonomousWarningPanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, 0, 0));

    private final TextFieldWithBrowseButton jacocoXmlPath = new TextFieldWithBrowseButton();
    private final JSpinner targetCoverageSpinner = new JSpinner(new SpinnerNumberModel(80, 1, 100, 1));
    private final JBTextArea exclusionsArea = new JBTextArea(6, 40);

    private final ComboBox<LlmProvider> providerCombo = new ComboBox<>(LlmProvider.values());
    private final JPanel providerCards = new JPanel(new CardLayout());

    private final JBTextField ollamaBaseUrlField = new JBTextField();
    private final JBTextField ollamaModelField = new JBTextField();
    private final JSpinner ollamaTemperatureSpinner = new JSpinner(new SpinnerNumberModel(0.1, 0.0, 1.0, 0.1));
    private final JSpinner ollamaTopPSpinner = new JSpinner(new SpinnerNumberModel(0.9, 0.0, 1.0, 0.05));
    private final JSpinner ollamaRepeatPenaltySpinner = new JSpinner(new SpinnerNumberModel(1.1, 0.1, 10.0, 0.1));

    private final JBPasswordField openAiApiKeyField = new JBPasswordField();
    private final JBTextField openAiModelField = new JBTextField();
    private final JSpinner openAiTemperatureSpinner = new JSpinner(new SpinnerNumberModel(0.1, 0.0, 1.0, 0.1));
    private final JSpinner openAiMaxTokensSpinner = new JSpinner(new SpinnerNumberModel(2048, 1, Integer.MAX_VALUE, 1));

    private final JBPasswordField gigaChatApiKeyField = new JBPasswordField();
    private final JBTextField gigaChatModelField = new JBTextField();
    private final JBTextField gigaChatEndpointField = new JBTextField();

    private final JBCheckBox strictModeCheck = new JBCheckBox("Strict mode");
    private final JBCheckBox contextCacheCheck = new JBCheckBox("Context cache enabled");
    private final JBCheckBox verboseLoggingCheck = new JBCheckBox("Verbose logging");

    private ComponentValidator maxIterationsValidator;

    private String initialOpenAiKey = "";
    private String initialGigaChatKey = "";

    public TestSmithSettingsPanel(Project project) {
        jacocoXmlPath.addBrowseFolderListener(
                "Select JaCoCo XML",
                null,
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor("xml")
        );

        exclusionsArea.setLineWrap(false);

        configureAutonomousWarning();
        configureProviderCards();
        configureListeners();
        configureValidation();

        FormBuilder builder = FormBuilder.createFormBuilder();
        builder.addComponent(new com.intellij.ui.TitledSeparator("Build & Execution"));
        builder.addLabeledComponent("Build tool:", buildToolModeCombo);
        builder.addLabeledComponent("Execution mode:", executionModeCombo);
        builder.addLabeledComponent("Max iterations:", maxIterationsSpinner);
        builder.addComponent(autonomousWarningPanel);

        builder.addComponent(new com.intellij.ui.TitledSeparator("JaCoCo"));
        builder.addLabeledComponent("JaCoCo XML path:", jacocoXmlPath);
        JBLabel jacocoHint = new JBLabel("Leave empty to auto-detect.");
        jacocoHint.setForeground(UIUtil.getContextHelpForeground());
        builder.addComponent(jacocoHint);
        builder.addLabeledComponent("Target coverage (%):", targetCoverageSpinner);
        builder.addLabeledComponent("Exclusions (one per line):", new JBScrollPane(exclusionsArea));

        builder.addComponent(new com.intellij.ui.TitledSeparator("LLM Provider"));
        builder.addLabeledComponent("Provider:", providerCombo);
        builder.addComponent(providerCards);

        builder.addComponent(new com.intellij.ui.TitledSeparator("Advanced & Safety"));
        builder.addComponent(strictModeCheck);
        builder.addComponent(contextCacheCheck);
        builder.addComponent(verboseLoggingCheck);

        root = new JBPanel<>(new BorderLayout());
        root.add(builder.getPanel(), BorderLayout.NORTH);
    }

    public JComponent getComponent() {
        return root;
    }

    public JComponent getPreferredFocusedComponent() {
        return buildToolModeCombo;
    }

    public void reset(TestSmithProjectSettings settings, String openAiKey, String gigaChatKey) {
        buildToolModeCombo.setSelectedItem(settings.buildToolMode);
        executionModeCombo.setSelectedItem(settings.executionMode);
        maxIterationsSpinner.setValue(settings.maxIterations);

        jacocoXmlPath.setText(settings.jacocoXmlPath == null ? "" : settings.jacocoXmlPath);
        targetCoverageSpinner.setValue(settings.targetCoverage);
        exclusionsArea.setText(String.join("\n", safeList(settings.exclusions)));

        providerCombo.setSelectedItem(settings.provider);

        TestSmithProjectSettings.OllamaConfig ollama = settings.ollama == null ? new TestSmithProjectSettings.OllamaConfig() : settings.ollama;
        ollamaBaseUrlField.setText(nullToEmpty(ollama.baseUrl));
        ollamaModelField.setText(nullToEmpty(ollama.model));
        ollamaTemperatureSpinner.setValue(ollama.temperature);
        ollamaTopPSpinner.setValue(ollama.topP);
        ollamaRepeatPenaltySpinner.setValue(ollama.repeatPenalty);

        TestSmithProjectSettings.OpenAiConfig openAi = settings.openAi == null ? new TestSmithProjectSettings.OpenAiConfig() : settings.openAi;
        openAiModelField.setText(nullToEmpty(openAi.model));
        openAiTemperatureSpinner.setValue(openAi.temperature);
        openAiMaxTokensSpinner.setValue(openAi.maxTokens);

        TestSmithProjectSettings.GigaChatConfig gigaChat = settings.gigaChat == null ? new TestSmithProjectSettings.GigaChatConfig() : settings.gigaChat;
        gigaChatModelField.setText(nullToEmpty(gigaChat.model));
        gigaChatEndpointField.setText(nullToEmpty(gigaChat.endpoint));

        strictModeCheck.setSelected(settings.strictMode);
        contextCacheCheck.setSelected(settings.contextCacheEnabled);
        verboseLoggingCheck.setSelected(settings.verboseLogging);

        markClean(openAiKey, gigaChatKey);
        openAiApiKeyField.setText(initialOpenAiKey);
        gigaChatApiKeyField.setText(initialGigaChatKey);

        updateAutonomousControls();
        updateProviderCard();
    }

    public boolean isModified(TestSmithProjectSettings settings) {
        TestSmithProjectSettings.OllamaConfig ollama = settings.ollama == null ? new TestSmithProjectSettings.OllamaConfig() : settings.ollama;
        TestSmithProjectSettings.OpenAiConfig openAi = settings.openAi == null ? new TestSmithProjectSettings.OpenAiConfig() : settings.openAi;
        TestSmithProjectSettings.GigaChatConfig gigaChat = settings.gigaChat == null ? new TestSmithProjectSettings.GigaChatConfig() : settings.gigaChat;

        if (!Objects.equals(buildToolModeCombo.getSelectedItem(), settings.buildToolMode)) {
            return true;
        }
        if (!Objects.equals(executionModeCombo.getSelectedItem(), settings.executionMode)) {
            return true;
        }
        if (intValue(maxIterationsSpinner) != settings.maxIterations) {
            return true;
        }
        if (!Objects.equals(jacocoXmlPath.getText().trim(), nullToEmpty(settings.jacocoXmlPath))) {
            return true;
        }
        if (intValue(targetCoverageSpinner) != settings.targetCoverage) {
            return true;
        }
        if (!Objects.equals(parseExclusions(), safeList(settings.exclusions))) {
            return true;
        }
        if (!Objects.equals(providerCombo.getSelectedItem(), settings.provider)) {
            return true;
        }
        if (!Objects.equals(ollamaBaseUrlField.getText().trim(), nullToEmpty(ollama.baseUrl))) {
            return true;
        }
        if (!Objects.equals(ollamaModelField.getText().trim(), nullToEmpty(ollama.model))) {
            return true;
        }
        if (differs(doubleValue(ollamaTemperatureSpinner), ollama.temperature)) {
            return true;
        }
        if (differs(doubleValue(ollamaTopPSpinner), ollama.topP)) {
            return true;
        }
        if (differs(doubleValue(ollamaRepeatPenaltySpinner), ollama.repeatPenalty)) {
            return true;
        }
        if (!Objects.equals(openAiModelField.getText().trim(), nullToEmpty(openAi.model))) {
            return true;
        }
        if (differs(doubleValue(openAiTemperatureSpinner), openAi.temperature)) {
            return true;
        }
        if (intValue(openAiMaxTokensSpinner) != openAi.maxTokens) {
            return true;
        }
        if (!Objects.equals(gigaChatModelField.getText().trim(), nullToEmpty(gigaChat.model))) {
            return true;
        }
        if (!Objects.equals(gigaChatEndpointField.getText().trim(), nullToEmpty(gigaChat.endpoint))) {
            return true;
        }
        if (strictModeCheck.isSelected() != settings.strictMode) {
            return true;
        }
        if (contextCacheCheck.isSelected() != settings.contextCacheEnabled) {
            return true;
        }
        if (verboseLoggingCheck.isSelected() != settings.verboseLogging) {
            return true;
        }
        if (!Objects.equals(getOpenAiKey(), initialOpenAiKey)) {
            return true;
        }
        return !Objects.equals(getGigaChatKey(), initialGigaChatKey);
    }

    public void applyTo(TestSmithProjectSettings settings) {
        settings.buildToolMode = (BuildToolMode) buildToolModeCombo.getSelectedItem();
        settings.executionMode = (ExecutionMode) executionModeCombo.getSelectedItem();
        settings.maxIterations = intValue(maxIterationsSpinner);

        settings.jacocoXmlPath = jacocoXmlPath.getText().trim();
        settings.targetCoverage = intValue(targetCoverageSpinner);
        settings.exclusions = parseExclusions();

        settings.provider = (LlmProvider) providerCombo.getSelectedItem();

        if (settings.ollama == null) {
            settings.ollama = new TestSmithProjectSettings.OllamaConfig();
        }
        settings.ollama.baseUrl = ollamaBaseUrlField.getText().trim();
        settings.ollama.model = ollamaModelField.getText().trim();
        settings.ollama.temperature = doubleValue(ollamaTemperatureSpinner);
        settings.ollama.topP = doubleValue(ollamaTopPSpinner);
        settings.ollama.repeatPenalty = doubleValue(ollamaRepeatPenaltySpinner);

        if (settings.openAi == null) {
            settings.openAi = new TestSmithProjectSettings.OpenAiConfig();
        }
        settings.openAi.model = openAiModelField.getText().trim();
        settings.openAi.temperature = doubleValue(openAiTemperatureSpinner);
        settings.openAi.maxTokens = intValue(openAiMaxTokensSpinner);

        if (settings.gigaChat == null) {
            settings.gigaChat = new TestSmithProjectSettings.GigaChatConfig();
        }
        settings.gigaChat.model = gigaChatModelField.getText().trim();
        settings.gigaChat.endpoint = gigaChatEndpointField.getText().trim();

        settings.strictMode = strictModeCheck.isSelected();
        settings.contextCacheEnabled = contextCacheCheck.isSelected();
        settings.verboseLogging = verboseLoggingCheck.isSelected();
    }

    public ValidationInfo validateForApply() {
        if (executionModeCombo.getSelectedItem() == ExecutionMode.AUTONOMOUS) {
            if (intValue(maxIterationsSpinner) <= 0) {
                return new ValidationInfo("Max iterations must be greater than 0.", maxIterationsSpinner);
            }
        }
        if (strictModeCheck.isSelected()) {
            String path = jacocoXmlPath.getText().trim();
            if (!path.isEmpty()) {
                ValidationInfo info = validateJacocoPath();
                if (info != null) {
                    return info;
                }
            }
        }
        LlmProvider provider = (LlmProvider) providerCombo.getSelectedItem();
        if (provider == LlmProvider.OPENAI && getOpenAiKey().isEmpty()) {
            return new ValidationInfo("OpenAI API key is required.", openAiApiKeyField);
        }
        if (provider == LlmProvider.GIGACHAT && getGigaChatKey().isEmpty()) {
            return new ValidationInfo("GigaChat API key is required.", gigaChatApiKeyField);
        }
        return null;
    }

    public ExecutionMode getExecutionMode() {
        return (ExecutionMode) executionModeCombo.getSelectedItem();
    }

    public String getOpenAiKey() {
        return new String(openAiApiKeyField.getPassword()).trim();
    }

    public String getGigaChatKey() {
        return new String(gigaChatApiKeyField.getPassword()).trim();
    }

    public void markClean(String openAiKey, String gigaChatKey) {
        initialOpenAiKey = openAiKey == null ? "" : openAiKey;
        initialGigaChatKey = gigaChatKey == null ? "" : gigaChatKey;
    }

    private void configureAutonomousWarning() {
        autonomousWarningPanel.add(new JBLabel(AllIcons.General.Warning));
        JBLabel label = new JBLabel("Autonomous mode runs iterative test generation without per-step approval. You can stop the agent at any time.");
        label.setForeground(UIUtil.getContextHelpForeground());
        autonomousWarningPanel.add(Box.createHorizontalStrut(6));
        autonomousWarningPanel.add(label);
    }

    private void configureProviderCards() {
        providerCards.add(buildOllamaPanel(), LlmProvider.OLLAMA.name());
        providerCards.add(buildOpenAiPanel(), LlmProvider.OPENAI.name());
        providerCards.add(buildGigaChatPanel(), LlmProvider.GIGACHAT.name());
    }

    private void configureListeners() {
        executionModeCombo.addActionListener(event -> updateAutonomousControls());
        providerCombo.addActionListener(event -> updateProviderCard());
    }

    private void updateAutonomousControls() {
        boolean autonomous = executionModeCombo.getSelectedItem() == ExecutionMode.AUTONOMOUS;
        maxIterationsSpinner.setEnabled(autonomous);
        autonomousWarningPanel.setVisible(autonomous);
        if (maxIterationsValidator != null) {
            maxIterationsValidator.revalidate();
        }
    }

    private void updateProviderCard() {
        LlmProvider provider = (LlmProvider) providerCombo.getSelectedItem();
        CardLayout layout = (CardLayout) providerCards.getLayout();
        layout.show(providerCards, provider == null ? LlmProvider.OLLAMA.name() : provider.name());
    }

    private void configureValidation() {
        installValidator(targetCoverageSpinner, this::validateTargetCoverage);
        maxIterationsValidator = installValidator(maxIterationsSpinner, this::validateMaxIterations);
        installValidator(jacocoXmlPath.getTextField(), this::validateJacocoPath).andRegisterOnDocumentListener(jacocoXmlPath.getTextField());
        installValidator(ollamaBaseUrlField, this::validateOllamaBaseUrl).andRegisterOnDocumentListener(ollamaBaseUrlField);
        installValidator(ollamaTemperatureSpinner, () -> validateZeroToOne("Temperature must be between 0 and 1.", ollamaTemperatureSpinner));
        installValidator(ollamaTopPSpinner, () -> validateZeroToOne("Top P must be between 0 and 1.", ollamaTopPSpinner));
        installValidator(ollamaRepeatPenaltySpinner, this::validateRepeatPenalty);
        installValidator(openAiTemperatureSpinner, () -> validateZeroToOne("Temperature must be between 0 and 1.", openAiTemperatureSpinner));
    }

    private ComponentValidator installValidator(JComponent component, Supplier<ValidationInfo> supplier) {
        ComponentValidator validator = new ComponentValidator(disposable)
                .withValidator(supplier::get)
                .installOn(component);
        if (component instanceof JSpinner spinner) {
            spinner.addChangeListener(event -> validator.revalidate());
        }
        return validator;
    }

    private ValidationInfo validateTargetCoverage() {
        int value = intValue(targetCoverageSpinner);
        if (value < 1 || value > 100) {
            return new ValidationInfo("Target coverage must be between 1 and 100.", targetCoverageSpinner);
        }
        return null;
    }

    private ValidationInfo validateMaxIterations() {
        if (executionModeCombo.getSelectedItem() != ExecutionMode.AUTONOMOUS) {
            return null;
        }
        if (intValue(maxIterationsSpinner) <= 0) {
            return new ValidationInfo("Max iterations must be greater than 0.", maxIterationsSpinner);
        }
        return null;
    }

    private ValidationInfo validateOllamaBaseUrl() {
        String value = ollamaBaseUrlField.getText().trim();
        if (value.isEmpty()) {
            return new ValidationInfo("Base URL is required.", ollamaBaseUrlField);
        }
        try {
            new URL(value);
        } catch (MalformedURLException e) {
            return new ValidationInfo("Base URL must be a valid URL.", ollamaBaseUrlField);
        }
        return null;
    }

    private ValidationInfo validateRepeatPenalty() {
        double value = doubleValue(ollamaRepeatPenaltySpinner);
        if (value <= 0) {
            return new ValidationInfo("Repeat penalty must be greater than 0.", ollamaRepeatPenaltySpinner);
        }
        return null;
    }

    private ValidationInfo validateZeroToOne(String message, JSpinner spinner) {
        double value = doubleValue(spinner);
        if (value < 0 || value > 1) {
            return new ValidationInfo(message, spinner);
        }
        return null;
    }

    private ValidationInfo validateJacocoPath() {
        String path = jacocoXmlPath.getText().trim();
        if (path.isEmpty()) {
            return null;
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            return new ValidationInfo("JaCoCo XML file does not exist.", jacocoXmlPath.getTextField());
        }
        if (!file.canRead()) {
            return new ValidationInfo("JaCoCo XML file is not readable.", jacocoXmlPath.getTextField());
        }
        if (!path.endsWith(".xml")) {
            return new ValidationInfo("JaCoCo XML path must end with .xml.", jacocoXmlPath.getTextField());
        }
        return null;
    }

    private JPanel buildOllamaPanel() {
        FormBuilder builder = FormBuilder.createFormBuilder();
        builder.addLabeledComponent("Base URL:", ollamaBaseUrlField);
        builder.addLabeledComponent("Model:", ollamaModelField);
        builder.addLabeledComponent("Temperature:", ollamaTemperatureSpinner);
        builder.addLabeledComponent("Top P:", ollamaTopPSpinner);
        builder.addLabeledComponent("Repeat penalty:", ollamaRepeatPenaltySpinner);
        return builder.getPanel();
    }

    private JPanel buildOpenAiPanel() {
        FormBuilder builder = FormBuilder.createFormBuilder();
        builder.addLabeledComponent("API key:", openAiApiKeyField);
        builder.addLabeledComponent("Model:", openAiModelField);
        builder.addLabeledComponent("Temperature:", openAiTemperatureSpinner);
        builder.addLabeledComponent("Max tokens:", openAiMaxTokensSpinner);
        return builder.getPanel();
    }

    private JPanel buildGigaChatPanel() {
        FormBuilder builder = FormBuilder.createFormBuilder();
        builder.addLabeledComponent("API key:", gigaChatApiKeyField);
        builder.addLabeledComponent("Model:", gigaChatModelField);
        builder.addLabeledComponent("Endpoint (optional):", gigaChatEndpointField);
        return builder.getPanel();
    }

    private List<String> parseExclusions() {
        String text = exclusionsArea.getText();
        String[] lines = text.split("\\r?\\n");
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static List<String> safeList(List<String> list) {
        return list == null ? List.of() : list;
    }

    private static double doubleValue(JSpinner spinner) {
        Object value = spinner.getValue();
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private static int intValue(JSpinner spinner) {
        Object value = spinner.getValue();
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static boolean differs(double a, double b) {
        return Math.abs(a - b) > 1e-6;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void dispose() {
        Disposer.dispose(disposable);
    }
}
