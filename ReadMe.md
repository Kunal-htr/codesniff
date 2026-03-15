<div align="center">

<img src="https://img.shields.io/badge/version-v0.5-blue?style=for-the-badge" alt="version"/>
<img src="https://img.shields.io/badge/java-17-orange?style=for-the-badge&logo=java" alt="java"/>
<img src="https://img.shields.io/badge/spring%20boot-3.3.5-green?style=for-the-badge&logo=springboot" alt="spring boot"/>
<img src="https://img.shields.io/badge/license-MIT-purple?style=for-the-badge" alt="license"/>
<img src="https://img.shields.io/badge/status-live-success?style=for-the-badge" alt="status"/>

# {CodeSniff}
### AI-Based Code Plagiarism Detector

**Detect code similarity instantly using K-gram tokenization and Winnowing algorithm**

[🌐 Live Demo](https://codesniff.tech) · [📝 Report Bug](https://github.com/Kunal-htr/codesniff/issues) · [✨ Request Feature](https://github.com/Kunal-htr/codesniff/issues)

</div>

---

## About

> *"In a world where code is copied, CodeSniff sees what eyes can't."*

CodeSniff is a **token-based code similarity analyzer** designed to detect 
plagiarism in **Java source files**. It uses the K-gram fingerprinting technique 
combined with the Winnowing algorithm — the same approach used by Stanford's 
MOSS system — to identify copied code even when variable names are changed, 
comments are removed, or statements are reordered.

Currently supporting **Java** with plans to expand to Python, C++, JavaScript 
and more in future releases through AI-powered semantic analysis.

---

## 🚀 Features

> ⚠️ **Current Version (v0.5):** Supports Java source files only. Multi-language support planned for v2.5.

- 🔍 **Token-based similarity detection** using K-gram algorithm
- 🪟 **Winnowing algorithm** for efficient fingerprint selection
- ☕ **Java source file** upload and pairwise comparison
- 💻 **Direct code paste** for quick Java code analysis
- 📊 **Similarity percentage** results table
- ⚙️ **Configurable options** — K-gram size, window size, ignore comments
- 🌐 **Fully deployed** on cloud infrastructure
---

## 🛠️ Tech Stack

### Frontend
| Technology | Purpose |
|---|---|
| HTML5 / CSS3 | UI structure and styling |
| Vanilla JavaScript | SPA routing and API calls |
| Vercel | Hosting and deployment |

### Backend
| Technology | Purpose |
|---|---|
| Java 17 | Core language |
| Spring Boot 3.3.5 | REST API framework |
| Maven | Build and dependency management |
| Azure App Service F1 | Cloud hosting (24/7) |

### Database & Infrastructure
| Technology | Purpose |
|---|---|
| PostgreSQL (Supabase) | Database |
| GitHub Actions | CI/CD pipeline |
| Nginx | Reverse proxy |

---

## 🏗️ System Architecture
```
User Browser
     │
     ▼
┌─────────────────┐
│  Vercel          │  codesniff.tech
│  (Frontend)      │  HTML + CSS + JS
└────────┬────────┘
         │ API calls
         ▼
┌─────────────────────────┐
│  Azure App Service F1    │  codesniff-backend.azurewebsites.net
│  Spring Boot Backend     │
│  ┌─────────────────┐    │
│  │AnalyzeController│    │
│  └────────┬────────┘    │
│           │              │
│  ┌────────▼────────┐    │
│  │SimilarityEngine  │    │
│  └────────┬────────┘    │
│           │              │
│  ┌────────▼────────┐    │
│  │Tokenizer         │    │
│  └────────┬────────┘    │
│           │              │
│  ┌────────▼────────┐    │
│  │CodeNormalizer    │    │
│  └─────────────────┘    │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────┐
│  Supabase        │  PostgreSQL Database
│  (Database)      │
└─────────────────┘
```

---

## 🔬 Detection Pipeline
```
Input Code
    │
    ▼
1. Code Normalization    → Remove comments, whitespace, lowercase
    │
    ▼
2. Tokenization         → Convert code to token stream
    │
    ▼
3. K-gram Generation    → Create overlapping k-grams (default k=6)
    │
    ▼
4. Hashing              → Hash each k-gram
    │
    ▼
5. Winnowing            → Select minimum hashes per window
    │
    ▼
6. Fingerprint Compare  → Jaccard similarity between fingerprint sets
    │
    ▼
Similarity Score (0% - 100%)
```

---

## 📦 Clone Types Detected

| Clone Type | Description | Detected |
|---|---|---|
| **Type 1** | Exact copy | ✅ |
| **Type 2** | Renamed identifiers | ✅ |
| **Type 3** | Added/removed statements | ✅ |
| **Type 4** | Semantic similarity | ⚠️ Partial |

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Maven 3.9+
- PostgreSQL (or Supabase account)

### Installation
```bash
# Clone the repository
git clone https://github.com/Kunal-htr/codesniff.git

# Navigate to project
cd codesniff

# Install dependencies
mvn clean install
```

### Configuration

Create `src/main/resources/application.properties`:
```properties
server.port=9090
spring.datasource.url=jdbc:postgresql://your-db-host:5432/postgres
spring.datasource.username=your-username
spring.datasource.password=your-password
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=update
```

### Run Locally
```bash
mvn spring-boot:run
```

Open `http://localhost:9090` in your browser.

---

## 🔌 API Reference

### Analyze Code Similarity
```http
POST /api/analyze
Content-Type: application/json
```

**Request Body:**
```json
{
  "submissions": [
    { "name": "A.java", "content": "public class A { ... }" },
    { "name": "B.java", "content": "public class B { ... }" }
  ],
  "options": {
    "omitComments": true,
    "k": 6,
    "window": 4
  }
}
```

**Response:**
```json
{
  "summary": [
    {
      "a": "A.java",
      "b": "B.java",
      "score": 0.451
    }
  ]
}
```

### Health Check
```http
GET /api/health
```
```
CodeSniff is alive!
```

---

## 📁 Project Structure
```
codesniff/
├── src/
│   └── main/
│       ├── java/
│       │   └── backend/
│       │       ├── App.java                 # Spring Boot entry point
│       │       ├── AnalyzeController.java   # REST API endpoints
│       │       ├── SimilarityEngine.java    # Core detection logic
│       │       ├── Tokenizer.java           # Code tokenization
│       │       ├── CodeNormalizer.java      # Code preprocessing
│       │       └── CorsConfig.java          # CORS configuration
│       └── resources/
│           └── static/
│               ├── index.html               # Frontend UI
│               ├── app.js                   # Frontend logic
│               └── style.css                # Styling
├── frontend/                                # Vercel deployment
├── .github/workflows/                       # CI/CD pipeline
├── Dockerfile                               # Container config
└── pom.xml                                  # Maven config
```

---

## 🔄 CI/CD Pipeline
```
git push to main
      │
      ▼
GitHub Actions triggers
      │
      ▼
Maven build + test
      │
      ▼
Deploy to Azure App Service
      │
      ▼
Live in ~50 seconds ✅
```

---

## 📊 Performance

| Metric | Value |
|---|---|
| Average response time | ~200ms |
| Max file size | 1MB |
| Supported languages | All text-based |
| Concurrent comparisons | Multiple pairs |

---

## 📦 Modules

| Module | Name | Status | Version |
|---|---|---|---|
| Module 1 | Similarity Engine | ✅ Complete | v0.5 |
| Module 2 | UI & User Workflow | 🔄 Planned | v1.0 |
| Module 3 | Report Visualization | 🔄 Planned | v1.5 |
| Module 4 | Database & Storage | 🔄 Planned | v2.0 |
| Module 5 | Future AI Enhancements | 🔄 Planned | v2.5 |

---

## 📄 License

This project is licensed under the MIT License.

---

## 🙏 Acknowledgements

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Supabase](https://supabase.com)
- [Vercel](https://vercel.com)
- [Azure](https://azure.microsoft.com)
- Winnowing Algorithm — Schleimer, Wilkerson, Aiken (2003)

---

<div align="center">

Made with ❤️ by Kunal Patel

⭐ Star this repo if you found it helpful!

</div>
