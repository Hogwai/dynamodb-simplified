# Plan de publication — DynamoDB Simplified

## Objectif
Publier une librairie Java indépendante sur Maven Central, avec une API fluide pour DynamoDB, zéro dépendance framework.
L'app Micronaut existante devient un module demo séparé.

---

## Phase 1 — Restructuration multi-module

### 1.1 Séparer la lib de l'app demo

Architecture actuelle : un seul module `dynamodb-simplified` avec `io.micronaut.application`.

Architecture cible :

```
dynamodb-simplified/
├── dynamodb-simplified-core/        # la librairie pure (Java 17+, AWS SDK v2 only)
│   ├── build.gradle.kts
│   └── src/main/java/com/hogwai/dynamodb/simplified/
│       ├── DynamoSimplifiedClient.java
│       ├── builder/
│       ├── expression/
│       └── result/
│
├── dynamodb-simplified-demo/         # l'app Micronaut existante
│   ├── build.gradle.kts
│   └── src/
│
├── build.gradle.kts                  # root — plugins communs, pas de code
├── settings.gradle.kts               # include les deux modules
├── gradle.properties
├── gradlew / gradlew.bat
├── README.md
├── LICENSE
├── PLAN.md
└── .gitignore
```

### 1.2 Dépendances — `dynamodb-simplified-core`

```kotlin
// build.gradle.kts du core
plugins {
    java
    `java-library`
    `maven-publish`
    signing
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    // API = visible par les utilisateurs
    api("software.amazon.awssdk:dynamodb:2.20.+")
    api("software.amazon.awssdk:dynamodb-enhanced:2.20.+")

    // Implementation = cachée
    // (rien pour l'instant — la lib est pure wrapper)
}

// Tests
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.+")
    testImplementation("org.mockito:mockito-core:5.+")
}
```

### 1.3 Dépendances — `dynamodb-simplified-demo`

```kotlin
dependencies {
    implementation(project(":dynamodb-simplified-core"))
    implementation("io.micronaut:micronaut-http-server-netty:4.6.+")
    // ...le reste comme aujourd'hui, sans le `dynamodb`
    // pulsique le core l'apporte via le projet
}
```

---

## Phase 2 — Bug fixes

### 2.1 `UpdateBuilder.execute()` ne transmet pas `UpdateExpression`

**Observation** : `UpdateBuilder` a un champ `updateExpression` et une méthode `update(Consumer<UpdateExpression>)`, mais `execute()` ne l'utilise pas. La requête fait un `updateItem` complet qui remplace tout l'item.

**Fix** : Transformer `UpdateBuilder` en deux modes :
- **Mode item complet** (actuel, sans `update()`) — remplace tout l'item
- **Mode mise à jour partielle** (avec `update()`) — utilise `UpdateItemEnhancedRequest` avec une expression de mise à jour

```java
public T execute() {
    if (updateExpression != null && !updateExpression.isEmpty()) {
        return executeWithExpression();
    }
    return executeSimple();
}
```

### 2.2 `QueryBuilder.count()` ne doit pas tout fetch

```java
// À Remplacer
public long count() {
    return execute().size();
}

// Par
public long count() {
    PageIterable<T> pages = table.query(
        QueryEnhancedRequest.builder()
            .queryConditional(keyCondition)
            .scanIndexForward(scanIndexForward)
            .consistentRead(consistentRead)
            .build()
    );
    return pages.stream().mapToLong(page -> page.count()).sum();
}
```

Utiliser `Select: COUNT` si possible.

### 2.3 Centraliser `toAttributeValue()`

Créer `AttributeValueConverter` utilitaire :

```java
// dynamodb-simplified-core/src/main/java/com/hogwai/dynamodb/simplified/internal/AttributeValueConverter.java
package com.hogwai.dynamodb.simplified.internal;

public final class AttributeValueConverter {
    public static AttributeValue toAttributeValue(Object value) {
        // switch complet : null, String, Number, Boolean, byte[], List, Set, Map
    }
}
```

Supprimer les 6 copies dans les builders.

---

## Phase 3 — Fonctionnalités manquantes

### 3.1 Transactions

- `TableOperations.transactGetWrite()` → wrapper pour `transactGetItems` / `transactWriteItems`
- `TransactGetBuilder` / `TransactWriteBuilder` avec API fluide

### 3.2 Batch operations

- `TableOperations.batchWrite()` → wrapper `batchWriteItem`
- `TableOperations.batchGet()` → wrapper `batchGetItem`

### 3.3 `@Nullable` / `@NonNull`

Ajouter `org.jetbrains:annotations` ou `jakarta.annotation` pour la nullabilité.

```java
public @Nullable Optional<T> execute() { ... }
```

---

## Phase 4 — Tests

### 4.1 Tests unitaires

| Cible | Outil |
|-------|-------|
| `FilterExpression` | JUnit 5 — vérifier expression string, names, values |
| `ProjectionExpression` | JUnit 5 |
| `UpdateExpression` | JUnit 5 |
| `AttributeValueConverter` | JUnit 5 |
| Chaque builder | JUnit 5 + Mockito (mocker `DynamoDbTable`) |
| `DynamoSimplifiedClient` | JUnit 5 + Mockito |

### 4.2 Tests d'intégration

- Avec **DynamoDB Local** (docker) ou `testcontainers-dynamodb`
- Créer table, CRUD, query, scan, pagination, conditions

### 4.3 CI

```yaml
# .github/workflows/ci.yml
name: CI
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - run: ./gradlew :dynamodb-simplified-core:check
```

---

## Phase 5 — Publication Maven Central

### 5.1 Configuration `maven-publish` + `signing`

```kotlin
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "dynamodb-simplified-core"
            from(components["java"])

            pom {
                name = "DynamoDB Simplified"
                description = "Fluent wrapper for AWS DynamoDB Enhanced Client"
                url = "https://github.com/hogwai/dynamodb-simplified"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }
                developers {
                    developer {
                        id = "hogwai"
                        name = "Hogwai"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/hogwai/dynamodb-simplified.git"
                    developerConnection = "scm:git:ssh://github.com/hogwai/dynamodb-simplified.git"
                    url = "https://github.com/hogwai/dynamodb-simplified"
                }
            }
        }
    }
    repositories {
        maven {
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = project.findProperty("sonatypeUsername") as String?
                password = project.findProperty("sonatypePassword") as String?
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
```

### 5.2 Procédure Sonatype

1. Créer un compte [Sonatype JIRA](https://issues.sonatype.org/)
2. Demander un nouveau ticket "New Project" pour `com.hogwai` (ou `io.github.<username>`)
3. Vérifier la propriété du groupe (domaine ou GitHub)
4. Configurer GPG key pour signer les artefacts
5. Publier en staging → release

### 5.3 Workflow GitHub Actions pour publication

```yaml
# .github/workflows/publish.yml
name: Publish to Maven Central
on:
  release:
    types: [created]
jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - name: Publish
        env:
          SONATYPE_USERNAME: ${{ secrets.SONATYPE_USERNAME }}
          SONATYPE_PASSWORD: ${{ secrets.SONATYPE_PASSWORD }}
          SIGNING_KEY: ${{ secrets.SIGNING_KEY }}
          SIGNING_PASSWORD: ${{ secrets.SIGNING_PASSWORD }}
        run: ./gradlew :dynamodb-simplified-core:publish
```

---

## Phase 6 — Polish

### 6.1 Javadoc

Ajouter de la Javadoc sur toutes les classes et méthodes publiques. L'effort se concentre sur :

| Priorité | Fichiers |
|----------|----------|
| Haute | `DynamoSimplifiedClient`, `TableOperations` |
| Haute | Chaque builder : `QueryBuilder`, `PutBuilder`, etc. |
| Haute | `FilterExpression` |
| Haute | `PagedResult` |
| Moyenne | `ProjectionExpression`, `UpdateExpression` |

### 6.2 Commentaires en anglais

Remplacer les commentaires en français. Exemples :

```java
// AVANT
// Pour un count efficace, on utilise la requête avec select COUNT
return execute().size(); // Simplifié - voir note ci-dessous

// APRÈS
// Fetches all items and counts client-side (TODO: use Select: COUNT)
return execute().size();
```

### 6.3 Renommer `TableOperations` → `Table`

```java
// Avant
DynamoSimplifiedClient client = ...;
TableOperations<Post> table = client.table("posts", Post.class);

// Après
Table<Post> table = client.table("posts", Post.class);
```

Plus court et plus naturel.

### 6.4 Exceptions spécifiques

```java
public class DynamoSimplifiedException extends RuntimeException {
    // wrap des exceptions SDK
}

public class ConditionFailedException extends DynamoSimplifiedException {
    // échec de condition check (ex-ConditionalCheckFailedException)
}
```

---

## Roadmap

1. **Semaine 1** : Multi-module + fix bug UpdateBuilder + centraliser toAttributeValue
2. **Semaine 2** : Tests unitaires + CI
3. **Semaine 3** : Transactions + Batch + nullability
4. **Semaine 4** : Javadoc + polish + renommage
5. **Semaine 5** : Config Sonatype + GitHub Actions publish + première release

---

## Checklist finale avant publication

- [ ] `dynamodb-simplified-core` ne dépend que d'AWS SDK v2 (pas de Micronaut)
- [ ] Tests unitaires verts
- [ ] Tests d'intégration verts (DynamoDB Local)
- [ ] `UpdateBuilder.execute()` fonctionne en mode partiel
- [ ] `QueryBuilder.count()` utilise `Select: COUNT`
- [ ] `AttributeValueConverter` centralisé, plus de duplication
- [ ] Javadoc sur toutes les classes publiques
- [ ] `module-info.java` ou au minimum pas de conflit de modules
- [ ] `-sources.jar` et `-javadoc.jar` générés
- [ ] Signing GPG configuré
- [ ] POM avec licence, SCM, développeurs
- [ ] Publication staging → release OK sur Maven Central
