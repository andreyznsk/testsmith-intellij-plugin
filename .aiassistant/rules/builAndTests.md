---
apply: always
---

CODEX RULE — Build & Test Infrastructure Freeze (Hard Constraint)

Codex MUST NOT edit any build or test-infrastructure configuration files unless the task explicitly says
"ALLOW BUILD CHANGES" and a human approved it.

Build / infra files include (non-exhaustive):
- Gradle: build.gradle, build.gradle.kts, settings.gradle, settings.gradle.kts, gradle.properties
- Maven: pom.xml, .mvn/*
- CI: .github/workflows/*, Jenkinsfile
- Test platform wiring: junit-platform.properties, surefire/failsafe configs, test task configs

In particular, Codex MUST NOT add any JUnit 4 dependency or enable JUnit4 engines/adapters, including:
- testImplementation("junit:junit:4.13.2")
- any "junit:junit:*"
- vintage engine / junit-vintage
- any Gradle/Maven changes that pull JUnit4 transitively to make tests pass

If Codex believes JUnit4 is required:
1) STOP and explain why (with evidence from current project code/config),
2) propose it as a separate dedicated PR/iteration,
3) wait for explicit human approval.

If a forbidden build change was made accidentally:
- revert it in the same PR and proceed without it.
