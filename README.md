# MarcmanMixer - Parfum Management System

A professional JavaFX application for managing perfume recipes, ingredients (IFRA compliant), and automated mixing control via Arduino integration.

## 🎯 Features

### Core Functionality
- **Recipe Management**: Create, edit, and manage perfume recipes with ingredient percentages
- **IFRA Ingredients Database**: Comprehensive database of IFRA-compliant ingredients with CAS numbers
- **PDF Recipe Import**: Import recipes from PDF documents with automatic ingredient matching
- **Automated Mixing Control**: Control Arduino-based pumps for precise ingredient dispensing
- **Pin Mapping**: Visual pin allocation management for Arduino UID/PIN configuration
- **Database Integration**: SQLite database for persistent storage of recipes and ingredients

### Arduino Integration
- **Serial Communication**: Real-time communication with Arduino MASTER processors via USB-serial
- **Multi-Slave Support**: Control multiple Arduino SLAVE devices with unique UIDs
- **Pump Control**: Digital and PWM control for precision pumping
- **Auto-Discovery**: Automatic detection of connected Arduino devices
- **Command Logging**: Track all commands sent to Arduino devices

### User Interface
- **Modern JavaFX UI**: Clean and intuitive tab-based interface
- **Real-time Updates**: Live status updates for Arduino connections
- **Data Validation**: Comprehensive validation for ingredients and recipes
- **Error Handling**: Robust error handling with user-friendly messages

## 📋 Requirements

- **Java**: JDK 21 or higher
- **Maven**: 3.8+ (for building)
- **Hardware**: Arduino MASTER with compatible SLAVE devices (optional, for mixing functionality)

## 🚀 Getting Started

### Prerequisites

1. **Install Java 21+**
   - Download from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)
   - Set `JAVA_HOME` environment variable

2. **Install Maven 3.8+**
   - Download from [Maven](https://maven.apache.org/download.cgi)
   - Set `MAVEN_HOME` environment variable
   - Add `%MAVEN_HOME%\bin` to `PATH`

### Building the Project

```bash
# Clone the repository
git clone https://github.com/yourusername/MarcmanMixer.git
cd MarcmanMixer

# Build all modules
mvn clean install

# Build with tests skipped (faster)
mvn clean install -DskipTests
```

### Running the Application

**Option 1: Using Maven (Recommended)**
```bash
mvn javafx:run -pl app
```

**Option 2: Using the launcher script**
```bash
# Windows
launch_app.bat

# The script will:
# - Auto-detect Java and Maven
# - Build if necessary
# - Launch the application
```

**Option 3: Direct JAR execution**
```bash
# After building
java --module-path <javafx-path> --add-modules javafx.controls,javafx.fxml -jar app/target/app-1.0.0-SNAPSHOT.jar
```

## 📁 Project Structure

```
MarcmanMixer/
├── app/                    # Main application module
│   ├── src/main/java/     # Application entry point
│   └── src/main/resources/ # Resources (icons, styles)
├── core/                   # Core domain models and services
│   └── src/main/java/     # Business logic, models, ports
├── sqlite/                 # SQLite database implementation
│   └── src/main/java/     # Database manager, repositories
├── serial/                 # Serial communication module
│   └── src/main/java/     # Arduino communication, commands
├── ui/                     # JavaFX UI components
│   └── src/main/java/     # Views, controllers
├── data/                   # Sample data and import scripts
├── pom.xml                 # Root Maven POM
└── launch_app.bat          # Windows launcher script
```

## 🏗️ Architecture

The application follows a **clean architecture** pattern with clear separation of concerns:

- **Core**: Domain models and business logic (framework-independent)
- **SQLite**: Database implementation (adapter)
- **Serial**: Arduino communication (adapter)
- **UI**: JavaFX user interface (adapter)
- **App**: Application orchestration and dependency injection

### Module Dependencies

```
app
├── ui (JavaFX views)
├── core (business logic)
│   └── sqlite (database implementation)
└── serial (Arduino communication)
```

## 🎨 Usage

### Creating Recipes

1. Go to **Recipes** tab
2. Click **Import from PDF** to import existing recipes
   - PDF text is automatically parsed
   - Ingredients are matched against the IFRA database
3. Or create recipes manually:
   - Click **Add Recipe**
   - Enter recipe name and ingredients with percentages
   - Save the recipe

### Managing Ingredients

1. Go to **Ingredients IFRA** tab
2. View all ingredients from the database
3. Configure Arduino settings:
   - Click **Configure Arduino** for an ingredient
   - Set Arduino UID and PIN
   - Configure pump thresholds
4. Import ingredients from CSV (see `data/` folder)

### Arduino Control

1. Go to **Procesor MASTER** tab
2. Select COM port or use **Auto-detect**
3. Click **Connect**
4. Once connected:
   - Use **Mix Control** tab to execute mixing operations
   - View pin mappings in **Pin Mapper** tab
   - Monitor serial communication in **Procesor MASTER** tab

## 🔧 Configuration

### Database Location

The application automatically detects the database location:
- First run: Creates `app/marcman_mixer.db`
- Portable mode: Uses database in application directory
- Standard mode: Uses database in user's home directory

### Arduino Communication

- **Baud Rate**: 115200 (default)
- **Protocol**: Custom text-based commands
- **Command Format**: See `serial` module documentation

## 📦 Building Distribution

### Creating a Standalone Package

The project includes scripts for creating portable distributions:

```bash
# Note: Distribution scripts were removed during cleanup
# Use Maven to build and package:
mvn clean package -DskipTests
```

### Installer (MSI)

For Windows users, an MSI installer can be created using `jpackage`:

```bash
jpackage --type msi \
    --input "app/target" \
    --name "MarcmanMixer" \
    --main-jar "app-1.0.0-SNAPSHOT.jar" \
    --main-class "ro.marcman.mixer.app.App" \
    --app-version "1.0.0" \
    --vendor "Marcman" \
    --runtime-image "%JAVA_HOME%"
```

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run tests for specific module
mvn test -pl core
```

## 📚 Documentation

- **Architecture**: See source code comments for detailed architecture documentation
- **Arduino Protocol**: See `serial` module for command specifications
- **Database Schema**: Database is auto-created on first run

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

### Development Setup

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙏 Acknowledgments

- **JavaFX**: For the modern UI framework
- **jSerialComm**: For serial port communication
- **Apache PDFBox**: For PDF parsing
- **SQLite**: For embedded database
- **IFRA**: For ingredient safety guidelines

## 📧 Contact

For questions or support, please open an issue on GitHub.

---

**Made with ❤️ for perfume enthusiasts and professionals**

