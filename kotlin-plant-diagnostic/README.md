# Plant Disease Diagnostic System (Kotlin)

A Kotlin conversion of the Python plant disease diagnostic system using graph-based reasoning and LLM integration.

## Prerequisites

### 1. Install Java
```bash
# Install Java using Homebrew (macOS)
brew install openjdk@17

# Add to your shell profile
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# Verify installation
java -version
```

### 2. Install Kotlin (Optional - Gradle will handle this)
```bash
# Using Homebrew
brew install kotlin

# Or using SDKMAN
curl -s "https://get.sdkman.io" | bash
source ~/.sdkman/bin/sdkman-init.sh
sdk install kotlin
```

### 3. Install Ollama and Model
```bash
# Install Ollama
curl -fsSL https://ollama.ai/install.sh | sh

# Pull the required model
ollama pull gemma3n:e4b
```

## Project Structure

```
kotlin-plant-diagnostic/
├── build.gradle.kts          # Gradle build configuration
├── src/main/kotlin/com/plantdiagnostic/
│   ├── models/
│   │   └── GraphModels.kt     # Data models
│   ├── JsonGraphConnector.kt  # Graph data handling
│   ├── OllamaConnector.kt    # LLM integration
│   ├── DiagnosticSystem.kt   # Core diagnostic logic
│   └── Main.kt               # Application entry point
├── plant_disease_graph.json  # Graph data
└── README.md
```

## Installation Steps

1. **Clone/Setup the project:**
   ```bash
   cd kotlin-plant-diagnostic
   ```

2. **Set environment variables:**
   ```bash
   export OLLAMA_BASE_URL=http://localhost:11434
   export OLLAMA_MODEL=gemma3n:e4b
   ```

3. **Start Ollama:**
   ```bash
   ollama serve
   ```

4. **Build the project:**
   ```bash
   ./gradlew build
   ```

## Running the Application

### Option 1: Using Gradle
```bash
./gradlew run
```

### Option 2: Build and run JAR
```bash
./gradlew build
java -jar build/libs/kotlin-plant-diagnostic-1.0.0.jar
```

## Usage

Once running, the system provides an interactive diagnostic interface:

```
🌱 === PLANT DISEASE DIAGNOSTIC SYSTEM (GRAPH DIRECT) ===
🎯 DIRECT graph traversal - no LLM for symptom selection!
🔍 Knowledge graph drives the conversation flow!

🌿 PLANT DISEASE DIAGNOSTIC ASSISTANT (GRAPH DIRECT)
💡 Graph data directly drives symptom selection!
Commands: 'summary', 'state', 'reset', 'quit'

🌱 You: yellow leaves
```

### Available Commands:
- **Any symptom description**: Start or continue diagnosis
- **`summary`**: Get current diagnostic summary
- **`state`**: View current conversation state
- **`reset`**: Reset the conversation
- **`quit` or `exit`**: Exit the application

## Features

### Graph-Direct Approach
- ✅ **Direct graph traversal** for symptom selection
- ✅ **No LLM dependency** for conversation flow
- ✅ **Fuzzy symptom matching** with keyword synonyms
- ✅ **Intelligent symptom scoring** for optimal questions

### Diagnostic Capabilities
- 🎯 **Progressive diagnosis** based on confirmed symptoms
- 🔍 **Smart differentiating questions** from graph data
- 📊 **Confidence-based decision making**
- 💡 **Treatment recommendations** from graph solutions

### Technical Features
- ⚡ **Asynchronous HTTP calls** using Ktor
- 🔄 **JSON serialization** with kotlinx.serialization
- 🌐 **Environment configuration** with dotenv
- 📱 **Interactive CLI** with colored output

## Dependencies

- **Kotlin 1.9.10** - Programming language
- **Ktor 2.3.4** - HTTP client for Ollama API
- **kotlinx.serialization 1.6.0** - JSON handling
- **dotenv-kotlin 6.4.1** - Environment variables
- **kotlinx.coroutines 1.7.3** - Asynchronous programming

## Configuration

Set these environment variables:

```bash
export OLLAMA_BASE_URL=http://localhost:11434  # Ollama server URL
export OLLAMA_MODEL=gemma3n:e4b                # LLM model name
```

## Limitations

1. **LLM Dependency**: Requires running Ollama server for final diagnosis summaries
2. **Graph Data**: Uses static JSON graph - no dynamic updates
3. **Language**: English-only symptom matching
4. **Domain**: Limited to plant diseases in the graph data
5. **Concurrency**: Single-user console application

## Troubleshooting

### Common Issues:

1. **Java not found**: Install OpenJDK 17+
2. **Ollama connection failed**: 
   - Start Ollama: `ollama serve`
   - Check model: `ollama pull gemma3n:e4b`
3. **Build failures**: Ensure internet connection for dependency downloads
4. **Graph not loading**: Verify `plant_disease_graph.json` exists

### Debug Steps:
```bash
# Check Java
java -version

# Check Ollama
curl http://localhost:11434/api/tags

# Test build
./gradlew clean build

# Run with debug
./gradlew run --debug
```

## Comparison with Python Version

| Feature | Python | Kotlin |
|---------|---------|---------|
| **HTTP Client** | `requests` | `Ktor` |
| **JSON Handling** | `json` | `kotlinx.serialization` |
| **Environment** | `dotenv` | `dotenv-kotlin` |
| **Concurrency** | `threading` | `coroutines` |
| **Performance** | Interpreted | JVM-compiled |
| **Type Safety** | Runtime | Compile-time |

## Development

To modify or extend the system:

1. **Add new symptoms**: Update `plant_disease_graph.json`
2. **Modify matching logic**: Edit `JsonGraphConnector.kt`
3. **Change conversation flow**: Update `DiagnosticSystem.kt`
4. **Add new LLM providers**: Extend `OllamaConnector.kt`

## License

Same as original Python implementation. 