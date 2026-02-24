package io.testsmith.plugin.settings;

import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.project.Project;
import com.intellij.credentialStore.CredentialAttributes;
import java.util.Optional;

public final class TestSmithSecretsStore {
    private static final String SERVICE_PREFIX = "TestSmith/";
    private static final String OPENAI_KEY = "openai.apiKey";
    private static final String GIGACHAT_KEY = "gigachat.apiKey";

    public Optional<String> getOpenAiKey(Project project) {
        return getPassword(project, OPENAI_KEY);
    }

    public Optional<String> getGigaChatKey(Project project) {
        return getPassword(project, GIGACHAT_KEY);
    }

    public void setOpenAiKey(Project project, String value) {
        setPassword(project, OPENAI_KEY, value);
    }

    public void setGigaChatKey(Project project, String value) {
        setPassword(project, GIGACHAT_KEY, value);
    }

    public void clearOpenAiKey(Project project) {
        setPassword(project, OPENAI_KEY, null);
    }

    public void clearGigaChatKey(Project project) {
        setPassword(project, GIGACHAT_KEY, null);
    }

    private Optional<String> getPassword(Project project, String key) {
        String value = PasswordSafe.getInstance().getPassword(attributes(project, key));
        return Optional.ofNullable(value).filter(s -> !s.isBlank());
    }

    private void setPassword(Project project, String key, String value) {
        PasswordSafe.getInstance().setPassword(attributes(project, key), normalize(value));
    }

    private CredentialAttributes attributes(Project project, String key) {
        return new CredentialAttributes(serviceName(project), key);
    }

    private String serviceName(Project project) {
        String projectId = project.getLocationHash();
        if (projectId == null || projectId.isBlank()) {
            String basePath = project.getBasePath();
            projectId = project.getName() + (basePath == null ? "" : ":" + basePath);
        }
        return SERVICE_PREFIX + projectId;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
