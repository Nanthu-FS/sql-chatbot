# NANTHAKUMARAN S

**.NET Backend Engineer · Local AI / LLM Systems**

---

## CONTACT

- Email: nanthakumaran.sundarapandian@acldigital.com
- Phone: +91 75500 52122
- LinkedIn: linkedin.com/in/nanthakumaran
- Location: Sembakkam, Chennai 600073
- Open to international roles · Relocation ready

---

## CORE STRENGTHS

- Production .NET backend engineering & enterprise system deployment
- Local LLM / RAG & multi-agent orchestration pipelines (LangGraph)
- Building offline AI with zero cloud cost (Ollama-based systems)
- Full-stack development: API design, vector databases, real-time data streaming
- End-to-end system design & shipping

---

## TECHNICAL SKILLS

**Languages:** C# · Python · SQL · Java · JavaScript · TypeScript · VB.NET · HTML5 · CSS

**Backend & Frameworks:** ASP.NET Web Forms · .NET Framework · FastAPI · LangChain · LangGraph · Streamlit · Node.js

**Databases:** SQL Server · MySQL · PostgreSQL / Supabase · Chroma DB (vector store)

**AI / LLM:** Ollama · LangGraph agents · RAG & Graph-RAG · Vision LLMs · Token streaming (SSE/NDJSON) · Embeddings & semantic search

**Tools & Platforms:** Visual Studio · Git / GitHub · SSMS · Telerik RadControls · ADO.NET · Uvicorn · Docker basics

---

## EDUCATION

**B.Tech, Computer Science**
Crescent Institute of Science & Technology
2020 – 2025 · CGPA 6.28

---

## LANGUAGES

- Tamil — Native
- English — B1 (Cambridge)
- French — Conversational

---

## CERTIFICATIONS

- Full-Stack Web Dev — DevTown (2023)
- Business English B1 — Cambridge
- Angular Basics — Certified

---

## PROFILE

Software Engineer Trainee at ACL Digital with hands-on production experience in C#, ASP.NET Web Forms, SQL Server, and enterprise deployment workflows. Independently designed and shipped critical live systems — a company-wide Mail Scheduler, Employee History Audit engine, and dynamic email systems. Built and deployed eight production-grade AI applications: five fully-local systems on Ollama + LangChain/LangGraph (Graph-RAG, multi-agent triage, document Q&A), one real-time flight tracker on live ADS-B data, one AI fitness tracker with cloud persistence, and one API documentation generator with agentic pipelines. Seeking international roles bridging .NET backend expertise with modern AI tooling and local-first LLM systems.

---

## EXPERIENCE

### Software Engineer Trainee | ACL Digital
**Nov 2025 – Present**

*C# · ASP.NET Web Forms · SQL Server · ADO.NET · VB.NET · AngularJS · ReactJS · Telerik UI · Python · LangChain · Ollama*

- Built and maintained enterprise applications in C#, ASP.NET Web Forms, and .NET Framework on live production codebases
- Designed and deployed a **Mail Scheduler** — live system automating daily company-wide email dispatches with retry logic and error tracking
- Built an **Email Log system** capturing SMTP errors, HTML logs via StringBuilder, and unsent mail retry mechanisms
- Designed an **Employee History Audit** using SQL triggers (deleted pseudo-table) for full pre-update traceability; added validation via stored procedures
- Built a dynamic **Profile-Change email system** — HTML-table emails capturing old vs. new field values on each employee update
- Refactored hardcoded email routing to a web.config-driven model, eliminating deployment friction; integrated Telerik RadControls and resolved production rendering issues
- Diagnosed critical SQL issues in live systems: NULL violations, STRING_AGG truncation, INSERT column mismatches; QA-validated and signed off production deploys
- Mentored peer through system design and helped resolve cross-domain SQL query optimization

---

## SELECTED PROJECTS

### GeoSnap — Image Geolocation

**Fully Local AI · Python · FastAPI · Ollama (llama3.2-vision 11B) · piexif · Nominatim/OpenStreetMap · globe.gl WebGL**

Pinpoints where any photograph was taken: extracts EXIF GPS metadata in milliseconds, or falls back to a local vision LLM that reasons from architecture, signage, vegetation, and landmarks to predict location with confidence scoring.

- Engineered a geo-detective prompt extracting structured JSON (country, city, lat/lon, confidence, visual clues, reasoning) with a robust parser that survives malformed model output
- Streams analysis events over FastAPI NDJSON; pins drop live onto a 3D WebGL globe colour-coded by confidence level
- Zero cloud dependency — 7.8 GB model, reverse geocoding, and globe rendering all run fully offline on-device
- GitHub: Nanthu-FS/rag_app (feat/geosnap branch)

---

### Atlas — Knowledge Graph Builder (Graph-RAG)

**Fully Local AI · Python · LangGraph · LangChain · Ollama · ChromaDB · FastAPI · Custom Canvas Force-Directed Viz**

Extracts typed entities and relationships from any text, merging them into a persistent knowledge graph; answers relational queries ("How is X connected to Y?") via shortest-path BFS with natural-language explanations and animated graph highlighting.

- Built a dependency-free force-directed graph renderer from scratch on HTML5 canvas — custom physics simulation, drag/zoom/pan, and animated path-lighting of answer routes
- Implemented 4-node LangGraph agent: intent detection → entity resolution via semantic search → BFS shortest-path finding → streaming narration with real-time canvas animation
- Nodes colour-coded by type (Person, Organization, Place, Concept, Event, Product); entity deduplication and merging in real-time
- 100% local execution — no external APIs or cloud dependencies
- GitHub: Nanthu-FS/rag_app (feat/knowledge-graph-app branch)

---

### Support Triage Bot — Multi-Agent Triage System

**Fully Local AI · Python · LangGraph · LangChain · Ollama · ChromaDB · FastAPI · NDJSON Streaming**

Orchestrated agentic pipeline that classifies incoming support messages by intent, retrieves answers from isolated domain-specific knowledge bases via RAG, scores confidence, and escalates to human agents when thresholds are hit. Runs entirely on local Ollama models — zero cloud, zero API cost, strict data-residency compliance.

- Designed LangGraph state machine (classify → retrieve → gate → respond | escalate) with confidence-based escalation logic that opens priority tickets on frustration signals, low model confidence, or knowledge-base misses
- Built multi-domain architecture: separate ChromaDB vector store per domain (billing, technical, general) prevents cross-domain retrieval leakage and enables per-domain tuning
- Real-time graph visualization in browser shows routing decisions live as messages flow through the pipeline
- Integrated persona-based multi-agent design where each specialist agent is trained on isolated domain markdown knowledge files
- GitHub: Nanthu-FS/rag_app (feat/support-triage-bot branch)

---

### API Documentation Generator

**Fully Local AI · Python · Streamlit · LangGraph · LangChain · Ollama · FastAPI**

LangGraph 5-node agentic pipeline that ingests API source code (FastAPI, Flask, Django, Express, Go, Spring Boot, etc.), auto-detects the framework, extracts all endpoints, and outputs professional Markdown documentation plus a downloadable OpenAPI 3.0 YAML specification — 100% locally via Ollama.

- **Node 1 — Detect Framework:** LLM classifies framework and language; keyword fallback ensures robustness
- **Node 2 — Extract Endpoints:** Chunked file processing; LLM extracts HTTP methods, paths, parameters, request/response schemas, auth flags, and tags; deduplication by METHOD:PATH
- **Node 3 — Write Markdown:** Per-endpoint Markdown blocks with parameters table, request body, response codes, and HTTP examples
- **Node 4 — Generate OpenAPI 3.0 YAML:** Complete spec with paths, requestBody, components/schemas, and server info; fallback minimal builder if LLM output fails
- **Node 5 — AI Quality Review:** LLM audits generated docs for vague descriptions, missing auth notes, undocumented errors, and naming inconsistencies; returns structured review with improvement suggestions
- Tabbed UI with metrics (framework, language, endpoint count, doc size) and one-click downloads for both Markdown and YAML
- Zero external API calls — all generation runs locally
- GitHub: Nanthu-FS/sql-chatbot (claude/ollama-langchain-app-ideas-Mcrpw branch)

---

### RAG Document Assistant — Local Document Q&A

**Fully Local AI · Python · LangChain · Ollama · ChromaDB · Streamlit · PyMuPDF**

Answers natural-language questions over private PDFs, DOCX, and TXT files with zero cloud API dependency. Ingests via PyMuPDF, embeds with nomic-embed-text, persists vectors in ChromaDB, and retrieves relevant passages to ground responses.

- Token-by-token streaming into Streamlit chat UI with persistent multi-turn history and inline source citations (filename + page number)
- Runs Mistral-Small 24B or llama3.2 locally on RTX 4070 Ti Super
- ~800-character document chunks with 100-character overlap for semantic coherence
- 100% offline — no data transmission to external services
- GitHub: Nanthu-FS/rag_app (main branch)

---

### Xeno Live — Real-Time Flight Tracker

**Live Data · Python · OpenSky Network API · Leaflet · CARTO Basemap · Vanilla JavaScript**

Renders live ADS-B aircraft positions worldwide (~6,000 concurrent aircraft), each marker rotated to true heading and colour-coded by climb/cruise/descent state. Updates every 10 seconds with regional filtering, callsign search, and live fleet statistics.

- Built a stdlib Python proxy relaying OpenSky API server-side to bypass CORS; implements caching and optional OAuth2 for higher rate limits
- Leaflet map with colour-coded altitude states: black (cruise), lime-green (climbing), orange (descending)
- Region filters for World, Europe, and North America
- Live stats display: aircraft count, average altitude, highest speed, origin countries
- GitHub: Nanthu-FS/rag_app (feat/flight-tracker branch)

---

### Vital — AI Fitness Tracker

**Local AI + Cloud Sync · Python · Supabase (PostgreSQL) · Ollama (llama3.2 3B) · OpenFoodFacts API · Glassmorphic UI**

Calculates personalized fitness targets (BMI, BMR, TDEE, daily macros, hydration goals) from user body inputs; local LLM generates personalized coaching with workout and meal plans. Syncs to cloud via Supabase with automatic offline-first localStorage fallback.

- Profile analysis: BMI, BMR, TDEE calculations with AI coaching summaries
- Meal logging: search OpenFoodFacts API or add custom foods; automatic macro calculations by serving size
- Hydration tracking: glass-by-glass water logging against daily target
- Workout logging: record exercises with AI-powered suggestions
- Supabase persistence with identical user experience online or fully offline; schema with FK cascades and row-level security
- Glassmorphic frosted-glass mobile design aesthetic
- GitHub: Nanthu-FS/rag_app (feat/fitness-tracker branch)

---

### SQL Query Assistant — NL-to-SQL Chatbot

**Fully Local AI · Python · LangChain · Ollama · MySQL · Streamlit**

Lets non-technical users query a MySQL database in plain English. LangChain SQL chain translates natural-language questions to valid SQL, executes them live, and returns results rendered as dynamic charts or downloadable CSV/Excel.

- Multi-attempt retry logic: if a query fails, the system rephrases as simpler SELECT/FROM/JOIN/WHERE; then suggests aggregation (COUNT, SUM, AVG) with GROUP BY
- Read-only query enforcement: regex blocks INSERT/UPDATE/DELETE, only SELECT/SHOW/WITH allowed
- Results visualized as bar charts and pie charts via Plotly, or exported as CSV/Excel
- Automatic table schema inspection sidebar for users to understand database structure
- Zero external LLM API calls — fully local Ollama inference
- GitHub: Nanthu-FS/sql-chatbot (main branch)

---

## EARLIER WORK

- **Hotel Management System** — PHP · MySQL · Room booking and billing system
- **API Integration Dashboard** — REST API client with request/response logging
- **Grocery Store Android App** — Java · SQLite inventory management

---

## ADDITIONAL NOTES

- All AI projects run 100% locally with Ollama — zero cloud cost, zero external API keys
- Experience shipping production systems end-to-end from design to deployment
- Comfortable debugging live production issues and mentoring peers
- Active learner — built 8 full-stack AI applications independently in the past year
