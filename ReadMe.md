<div align="center">

<img src="https://img.shields.io/badge/version-v1.0-blue?style=for-the-badge" alt="version"/>
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
combined with the Winnowing algorithm the same approach used by Stanford's 
MOSS system to identify copied code even when variable names are changed, 
comments are removed, or statements are reordered.

Currently supporting **Java** with plans to expand to Python, C++, JavaScript 
and more in future releases through AI-powered semantic analysis.

---

## 🚀 Features

> ⚠️ **Current Version (v1.0):** Supports Java source files only. Multi-language support planned for future releases.

- 🔍 **Token-based similarity detection** using K-gram algorithm
- 🪟 **Winnowing algorithm** for efficient fingerprint selection
- 🌳 **AST Structural Comparison** via compiler-level parsing (JavaParser)
- 📜 **LCS Statement Alignment** for flow-level similarity checks
- ☕ **Java source file** upload and pairwise comparison
- 💻 **Direct code paste** for quick Java code analysis
- 📊 **Similarity percentage** results table and detailed visual report
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
| In-Memory Cache | Temporary report storage |
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
└─────────────────────────┘
```

---

## 🔬 Detection Pipeline

```
                            Source Code A / B
                             ┌───────┴───────┐
                             ▼               ▼
                        [Raw Code]      [Tokenizer]
                             │               │
                             │               ├──────────────────────┐
                             ▼               ▼                      ▼
                        [ASTBuilder]   [CodeNormalizer]     [StatementGrouper]
                             │               │                      │
                             ▼               ▼                      ▼
                       [ASTComparator]  [Winnowing]            [LcsEngine]
                             │               │                      │
                             ▼               ▼                      ▼
                         AST Score      Jaccard & Coverage      LCS Score
                             │               │                      │
                             └───────────────┼──────────────────────┘
                                             ▼
                                      [Hybrid Score]
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
```

### Run Locally
```bash
mvn spring-boot:run
```

Open `http://localhost:9090` in your browser.

---

## 🔌 API Reference

### Analyze Code Similarity (JSON)
```http
POST /api/analyze
Content-Type: application/json
```

**Request Body:**
```json
{
  "submissions": [
    { "name": "A.java", "content": "public class A { int sum(int[] a){int s=0;for(int i=0;i<a.length;i++){s+=a[i];}return s;} }" },
    { "name": "B.java", "content": "public class B { int total(int[] d){int t=0;for(int j=0;j<d.length;j++){t+=d[j];}return t;} }" }
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
      "score": 1.0,
      "jaccard": 1.0,
      "coverage": 1.0,
      "reportId": "d741ca12-a16f-40c2-9ee6-4e59f427ee0e"
    }
  ]
}
```

### Get Detailed Pairwise Report
```http
GET /api/report/{id}
```

**Response:**
```json
{
  "nameA": "A.java",
  "nameB": "B.java",
  "jaccard": 1.0,
  "coverage": 1.0,
  "lcs": 1.0,
  "ast": 1.0,
  "hybrid": 1.0,
  "verdict": "High",
  "verdictDescription": "High similarity detected. There is a high probability of direct copy/paste or minimal rewriting.",
  "operatorDivergent": false,
  "metadata": {
    "k": 6,
    "window": 4,
    "omitComments": true,
    "fingerprintMatchCount": 15,
    "fingerprintCountA": 15,
    "fingerprintCountB": 15
  }
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

## 🛡️ Input Validation & Error Handling

CodeSniff v1.0 introduces strict input validation and centralized exception handling. Requests are validated using **Jakarta Bean Validation** before reaching the analysis engine. 

### Constraints:
* `submissions`: Must have between **1 and 200** files per batch.
* `name` & `content`: Must not be blank.
* `k`: Must be between **3 and 64**.
* `window`: Must be between **1 and 128**.

### Error Response Format (`ApiErrorDTO`):
When a validation constraint is violated, or a report ID is not found, the API returns a structured error object instead of raw stack traces:
```json
{
  "error": "Bad Request",
  "message": "Validation failed: submissions[0].content: Submission content must not be blank",
  "status": 400,
  "timestamp": 1784260000000
}
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
│       │       ├── analysis/                # Core similarity engine & tokenization
│       │       ├── ast/                     # AST generator & tree comparator
│       │       ├── config/                  # CORS config beans
│       │       ├── controller/              # REST controller (AnalyzeController)
│       │       ├── dto/                     # Inbound/outbound request/response DTO records
│       │       ├── exception/               # Global exception handlers & custom errors
│       │       ├── service/                 # Business logic orchestration services
│       │       ├── store/                   # In-memory report caching repository
│       │       └── util/                    # Schedulers & utility helpers
│       └── resources/
│           └── static/
│               ├── index.html               # Frontend UI
│               ├── app.js                   # Frontend logic
│               └── style.css                # Styling
├── frontend/                                # Vercel deployment
├── test_samples/                            # Standard plagiarism test samples
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
| Module 2 | Report & Visualization | ✅ Complete | v1.0 |
| Module 3 | Login, Account Creation & Authorization | 🔄 Planned | v1.5 |
| Module 4 | Database & Storage Integration | 🔄 Planned | v2.0 |
| Module 5 | Future AI Integration | 🔄 Planned | v2.5 |

---

## 📄 License

This project is licensed under the MIT License.

---

## 🙏 Acknowledgements

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Vercel](https://vercel.com)
- [Azure](https://azure.microsoft.com)
- Winnowing Algorithm — Schleimer, Wilkerson, Aiken (2003)

---

<div align="center">

</div>
