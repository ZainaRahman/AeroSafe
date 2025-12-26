# 🌍 AeroSafe - Air Quality Monitoring & Prediction System

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![JavaFX](https://img.shields.io/badge/JavaFX-21.0.6-green.svg)
![License](https://img.shields.io/badge/license-MIT-brightgreen.svg)

**AeroSafe** is a comprehensive desktop application for monitoring, analyzing, and predicting air quality across the globe. Built with JavaFX, it provides real-time AQI (Air Quality Index) data, AI-powered predictions, and role-based dashboards for users, researchers, and government officials.

---

## 📋 Table of Contents

- [Features](#-features)
- [Screenshots](#-screenshots)
- [Technology Stack](#-technology-stack)
- [Prerequisites](#-prerequisites)
- [Installation](#-installation)
- [Configuration](#️-configuration)
- [Usage](#-usage)
- [User Roles](#-user-roles)
- [API Integration](#-api-integration)
- [Database Schema](#️-database-schema)
- [Project Structure](#-project-structure)
- [Contributing](#-contributing)
- [License](#-license)
- [Contact](#-contact)

---

## ✨ Features

### 🌐 **For All Users**
- 🗺️ **Interactive Global Map** - Click anywhere to check air quality
- 📊 **Real-time AQI Data** - Live air quality index from OpenWeatherMap API
- 🔮 **AI-Powered Predictions** - Machine learning algorithms predict tomorrow's AQI
- 📈 **7-Day Historical Trends** - Visualize air quality patterns
- 🎨 **Color-Coded AQI Guide** - Instant visual understanding of air quality levels
- 🔍 **Location Search** - Search any city or location worldwide
- 📝 **Search History** - Quick access to previously searched locations
- ⚠️ **Report Environmental Issues** - Submit concerns to government officials

### 🔬 **For Researchers**
- 🧪 **Detailed Pollutant Analysis** - View PM2.5, PM10, NO₂, O₃, SO₂, CO levels
- 🗄️ **Research Data Hub** - Store and manage datasets from multiple locations
- 📊 **Statistical Analysis** - Calculate averages, min/max, and trends
- 📥 **CSV Export** - Export data for external analysis (Excel, Python, R, SPSS)
- 👥 **Researcher Network** - Find and connect with fellow researchers
- 📚 **Publications Library** - Access air quality research papers
- 📍 **Multi-Location Tracking** - Compare air quality across cities

### 🏛️ **For Government Officials**
- 👥 **User Management** - View all users, researchers, and admins
- 📋 **Reports Dashboard** - Review citizen-submitted environmental reports
- ✅ **Report Resolution** - Mark issues as resolved with status tracking
- 🚨 **Public Alert System** - Issue health advisories and warnings
- 📊 **Policy Data Analytics** - 30-day AQI trends and statistics
- 📈 **Policy Report Generation** - Comprehensive data for decision-making
- 🎯 **Population Impact Analysis** - Track affected citizens
- 🔔 **Alert Management** - Create, view, and deactivate public alerts

---

## 📸 Screenshots

### User Dashboard
```
┌─────────────────────────────────────────────────┐
│  🌍 AeroSafe                                    │
│  ┌──────────────────────────────────────────┐  │
│  │  [Interactive Leaflet Map]               │  │
│  │  Click anywhere to view AQI              │  │
│  └──────────────────────────────────────────┘  │
│                                                 │
│  Current AQI: 45 (Good) 👍                      │
│  Predicted AQI: 48 → Stable                    │
│                                                 │
│  📊 7-Day Historical Chart                      │
│  [Line chart with tooltips]                     │
└─────────────────────────────────────────────────┘
```

### Researcher Dashboard
```
┌─────────────────────────────────────────────────┐
│  🔬 Researcher Dashboard                        │
│  ┌─────────┬─────────┬─────────┐              │
│  │ PM2.5   │ PM10    │ NO₂     │              │
│  │ 45.20   │ 78.50   │ 32.10   │              │
│  └─────────┴─────────┴─────────┘              │
│                                                 │
│  🗄️ Data Hub Table                             │
│  [Stored datasets with statistics]             │
└─────────────────────────────────────────────────┘
```

### Admin Dashboard
```
┌─────────────────────────────────────────────────┐
│  🏛️ Government Official Dashboard               │
│  📋 Reports: 87 | 📊 Policy Data                │
│  ✅ Resolved: 64 | ⚠️ Active Alerts: 3          │
│                                                 │
│  [User Management | Reports | Alerts]           │
└─────────────────────────────────────────────────┘
```

---

## 🛠️ Technology Stack

### **Core Technologies**
- **Java 21** - Modern Java with latest features
- **JavaFX 21.0.6** - Rich desktop UI framework
- **SQLite 3.45.1.0** - Embedded database
- **Maven** - Dependency management and build tool

### **APIs & Libraries**
- **OpenWeatherMap Air Pollution API** - Real-time air quality data
- **Nominatim OpenStreetMap API** - Geocoding and reverse geocoding
- **Leaflet.js** - Interactive map visualization
- **JSON** - Data parsing and handling

### **Key Features**
- **JavaFX WebView** - Embedded web content for maps
- **TableView** - Advanced data tables
- **LineChart** - Interactive data visualization
- **FXML** - Declarative UI design

---

## 📦 Prerequisites

Before you begin, ensure you have the following installed:

- **Java Development Kit (JDK) 21** or higher
  - Download: [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://openjdk.org/)
  
- **Maven 3.8+** (Optional - Maven wrapper included)
  - Download: [Apache Maven](https://maven.apache.org/download.cgi)

- **Git** (for cloning the repository)
  - Download: [Git](https://git-scm.com/downloads)

### **Check Installations**
```bash
java -version    # Should show Java 21+
mvn -version     # Should show Maven 3.8+
git --version    # Should show Git 2.x+
```

---

## 🚀 Installation

### **1. Clone the Repository**
```bash
git clone https://github.com/yourusername/aerosafe.git
cd aerosafe
```

### **2. Using Maven (Recommended)**
```bash
# Clean and compile
mvn clean compile

# Run the application
mvn javafx:run
```

### **3. Using Maven Wrapper (No Maven installation needed)**

**On Windows:**
```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd javafx:run
```

**On macOS/Linux:**
```bash
./mvnw clean compile
./mvnw javafx:run
```

### **4. Using IDE (IntelliJ IDEA / Eclipse)**

**IntelliJ IDEA:**
1. Open IntelliJ IDEA
2. File → Open → Select project directory
3. Right-click `Launcher.java` → Run 'Launcher.main()'

**Eclipse:**
1. Import as Maven Project
2. Right-click project → Run As → Java Application
3. Select `Launcher` as main class

---

## ⚙️ Configuration

### **1. OpenWeatherMap API Key Setup**

**Required for real-time air quality data:**

1. **Get a FREE API Key:**
   - Visit [OpenWeatherMap](https://openweathermap.org/api)
   - Sign up for a free account
   - Navigate to API Keys section
   - Copy your API key

2. **Configure the Application:**
   
   **Option A: Using Template (Recommended)**
   ```bash
   # Copy the template file
   cp src/main/resources/config.properties.template src/main/resources/config.properties
   
   # Edit config.properties and add your API key
   openweather.api.key=YOUR_API_KEY_HERE
   ```

   **Option B: Create config.properties manually**
   ```properties
   # src/main/resources/config.properties
   openweather.api.key=98e192f418b2437e52cb54df708958f9
   ```

3. **Verify Configuration:**
   - Run the application
   - Click on the map
   - You should see real-time AQI data

### **2. Database Configuration**

**Automatic Setup:** Database (`aerosafe.db`) is created automatically on first run.

**Location:** `./aerosafe.db` (project root directory)

**Backup (Optional):**
```bash
# Create backup
cp aerosafe.db aerosafe_backup.db

# Restore backup
cp aerosafe_backup.db aerosafe.db
```

---

## 📖 Usage

### **First Time Setup**

1. **Launch the Application**
   ```bash
   mvn javafx:run
   ```

2. **Sign Up**
   - Select your role: User / Researcher / Government Official
   - Enter username and password
   - Add location (optional)
   - Click "Register"

3. **Login**
   - Select the same role you signed up with
   - Enter credentials
   - Click "Login"

### **User Dashboard Usage**

**View Air Quality:**
1. Click anywhere on the interactive map
2. View current AQI and pollutant levels
3. See 7-day historical trends
4. Check AI-powered predictions

**Search Locations:**
1. Enter city name in search bar
2. Click "Search"
3. Map centers on location
4. Recent searches saved for quick access

**Report Issues:**
1. Click "Report Issue" button
2. Fill in the form (location, issue type, severity, description)
3. Submit report to government officials

### **Researcher Dashboard Usage**

**Collect Data:**
1. Click location on map
2. View detailed pollutant data (PM2.5, PM10, NO₂, O₃, SO₂, CO)
3. Click "Add to Data Hub"
4. Data stored in database

**Analyze Data:**
1. Navigate to "Data Hub" tab
2. View all collected datasets
3. Click "Calculate Statistics"
4. Review averages, ranges, and trends

**Export Data:**
1. Click "Export Data" button
2. Choose save location
3. Data exported as CSV
4. Open in Excel, Python, R, or SPSS

### **Admin Dashboard Usage**

**View Users:**
1. Click "Users" button
2. View tabs: Regular Users / Researchers / Admins
3. See usernames, locations, and counts

**Manage Reports:**
1. Click "Reports" button
2. View citizen-submitted reports
3. Select report → Click "View Details"
4. Click "Mark as Resolved" when addressed

**Issue Public Alerts:**
1. Click "Alerts" button
2. Select alert type and severity
3. Enter location and message
4. Click "Issue Alert"
5. Alert visible to all users

**Generate Policy Reports:**
1. Click "Policy Data" button
2. View 30-day AQI statistics
3. Click "Generate Policy Report"
4. Review comprehensive analysis

---

## 👥 User Roles

### **🙋 Regular User**
**Access Level:** Basic
**Features:**
- View current and predicted AQI
- Interactive map with location selection
- 7-day historical trends
- Search locations globally
- Report environmental issues

**Use Case:** Citizens monitoring air quality for health and planning outdoor activities

---

### **🔬 Researcher**
**Access Level:** Advanced
**Features:**
- All User features +
- Detailed pollutant data (6 parameters)
- Research data hub with storage
- Statistical analysis tools
- CSV export functionality
- Researcher networking
- Publications library

**Use Case:** Scientists and academics conducting air quality research

---

### **🏛️ Government Official (Admin)**
**Access Level:** Administrative
**Features:**
- View all users by type
- Review citizen reports
- Mark reports as resolved
- Issue public health alerts
- View 30-day AQI trends
- Generate policy reports
- Track population impact
- Alert management system

**Use Case:** Environmental agencies and policy makers

---

## 🌐 API Integration

### **OpenWeatherMap Air Pollution API**

**Endpoints Used:**

1. **Current Air Pollution:**
   ```
   http://api.openweathermap.org/data/2.5/air_pollution
   ?lat={lat}&lon={lon}&appid={API_KEY}
   ```
   - Returns: Current AQI and pollutant levels

2. **Historical Air Pollution:**
   ```
   http://api.openweathermap.org/data/2.5/air_pollution/history
   ?lat={lat}&lon={lon}&start={unix_time}&end={unix_time}&appid={API_KEY}
   ```
   - Returns: Historical data for date range

**Data Retrieved:**
- AQI (Air Quality Index)
- PM2.5 (Fine Particulate Matter)
- PM10 (Coarse Particulate Matter)
- NO₂ (Nitrogen Dioxide)
- O₃ (Ozone)
- SO₂ (Sulfur Dioxide)
- CO (Carbon Monoxide)

### **Nominatim OpenStreetMap API**

**Endpoints Used:**

1. **Geocoding (Search):**
   ```
   https://nominatim.openstreetmap.org/search
   ?q={location_name}&format=json
   ```
   - Converts location name to coordinates

2. **Reverse Geocoding:**
   ```
   https://nominatim.openstreetmap.org/reverse
   ?lat={lat}&lon={lon}&format=json
   ```
   - Converts coordinates to location name

---

## 🗄️ Database Schema

### **Tables:**

#### **1. users**
```sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    location TEXT
);
```

#### **2. researchers**
```sql
CREATE TABLE researchers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    location TEXT
);
```

#### **3. admin**
```sql
CREATE TABLE admin (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    location TEXT
);
```

#### **4. reports**
```sql
CREATE TABLE reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    reporter_name TEXT NOT NULL,
    location TEXT NOT NULL,
    issue_type TEXT NOT NULL,
    severity TEXT NOT NULL,
    aqi_value TEXT,
    description TEXT NOT NULL,
    contact TEXT,
    status TEXT DEFAULT 'Pending',
    submitted_date TEXT NOT NULL
);
```

#### **5. research_data**
```sql
CREATE TABLE research_data (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp TEXT,
    location TEXT,
    pm25 REAL,
    pm10 REAL,
    no2 REAL,
    o3 REAL,
    so2 REAL,
    co REAL
);
```

#### **6. alerts**
```sql
CREATE TABLE alerts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    alert_type TEXT,
    severity TEXT,
    location TEXT,
    message TEXT,
    created_date TEXT,
    status TEXT DEFAULT 'Active'
);
```

#### **7. aq_data**
```sql
CREATE TABLE aq_data (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    city TEXT,
    date TEXT,
    pm25 REAL,
    pm10 REAL,
    aqi INTEGER
);
```

---

## 📁 Project Structure

```
AeroSafe/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── module-info.java
│   │   │   └── com/example/aerotutorial/
│   │   │       ├── HelloApplication.java        # Main application
│   │   │       ├── Launcher.java                # Entry point
│   │   │       ├── LoginController.java         # Login logic
│   │   │       ├── SignupController.java        # Signup logic
│   │   │       ├── DashboardController.java     # User dashboard
│   │   │       ├── ResearcherDashboardController.java  # Researcher dashboard
│   │   │       ├── AdminDashboardController.java       # Admin dashboard
│   │   │       ├── ReportIssueController.java   # Issue reporting
│   │   │       ├── AQIFetcher.java              # API data fetching
│   │   │       ├── PredictionEngine.java        # AI predictions
│   │   │       ├── DBConnector.java             # Database connection
│   │   │       ├── DBSetup.java                 # Database initialization
│   │   │       ├── DatabaseMigration.java       # Schema migration
│   │   │       └── ConfigLoader.java            # Configuration
│   │   └── resources/
│   │       ├── config.properties                # API keys & config
│   │       ├── config.properties.template       # Config template
│   │       └── com/example/aerotutorial/
│   │           ├── login.fxml                   # Login UI
│   │           ├── signup.fxml                  # Signup UI
│   │           ├── dashboard.fxml               # User dashboard UI
│   │           ├── researcher_dashboard.fxml    # Researcher UI
│   │           ├── admin_dashboard.fxml         # Admin UI
│   │           ├── report_issue.fxml            # Report form UI
│   │           └── map.html                     # Interactive map
│   └── test/                                    # Unit tests
├── target/                                      # Compiled classes
├── pom.xml                                      # Maven configuration
├── mvnw                                         # Maven wrapper (Unix)
├── mvnw.cmd                                     # Maven wrapper (Windows)
├── aerosafe.db                                  # SQLite database
├── search_history.dat                           # User search history
├── README.md                                    # This file
└── LICENSE                                      # License file
```

---

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

### **Report Bugs**
1. Check if the issue already exists
2. Open a new issue with detailed description
3. Include steps to reproduce
4. Add screenshots if applicable

### **Suggest Features**
1. Open an issue with [Feature Request] tag
2. Describe the feature and use case
3. Explain why it would be beneficial

### **Submit Pull Requests**
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Commit with clear messages (`git commit -m 'Add amazing feature'`)
5. Push to your branch (`git push origin feature/amazing-feature`)
6. Open a Pull Request

### **Coding Standards**
- Follow Java naming conventions
- Add JavaDoc comments for public methods
- Write unit tests for new features
- Ensure code compiles without errors
- Test thoroughly before submitting

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2025 AeroSafe Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---


## 🙏 Acknowledgments

- **OpenWeatherMap** - For providing air quality data API
- **OpenStreetMap** - For geocoding services
- **Leaflet.js** - For interactive map library
- **JavaFX Community** - For excellent UI framework
- **Contributors** - Thank you to everyone who has contributed!

---

## 📊 Statistics

- **Lines of Code:** ~15,000+
- **Classes:** 15+
- **FXML Files:** 6
- **Database Tables:** 7
- **Supported Platforms:** Windows, macOS, Linux
- **API Integrations:** 2
- **User Roles:** 3

---

## 🎯 Roadmap

### **Version 1.1 (Q1 2026)**
- [ ] Advanced data visualization (heatmaps)


### **Version 1.2 (Q2 2026)**
- [ ] Machine learning improvements
- [ ] Real-time collaboration features
- [ ] Cloud backup and sync
- [ ] API for third-party integrations

### **Version 2.0 (Q3 2026)**
- [ ] Web-based version
- [ ] Advanced analytics dashboard
- [ ] Integration with more data sources
- [ ] White-label solution for organizations

---

## ⭐ Star History

If you find this project useful, please give it a star! ⭐

[![Star History Chart](https://api.star-history.com/svg?repos=yourusername/aerosafe&type=Date)](https://star-history.com/#yourusername/aerosafe&Date)

---

<div align="center">

**Built with ❤️ for a cleaner, healthier planet 🌍**

[⬆ Back to Top](#-aerosafe---air-quality-monitoring--prediction-system)

</div>

