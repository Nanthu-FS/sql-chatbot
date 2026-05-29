# SQL Query Assistant

A natural-language chatbot for your SQL database. Ask questions in plain English, and the assistant generates the SQL, runs it, and returns the results as tables, charts, and downloadable files — all powered locally by [Ollama](https://ollama.com), so no data or API keys leave your machine.

Built with [Streamlit](https://streamlit.io), [LangChain](https://www.langchain.com), [SQLAlchemy](https://www.sqlalchemy.org), and [Plotly](https://plotly.com).

## Features

- 💬 **Ask in plain English** — questions are translated to SQL automatically
- 🗄️ Connects to a **MySQL** database via SQLAlchemy / PyMySQL
- 📊 Auto-renders results as **tables, bar charts, and pie charts**
- 📥 **Export** query results to CSV or Excel
- 🔁 Built-in **retry logic** that rephrases failed queries
- 🔒 Fully local LLM inference through Ollama — no external API calls

## Prerequisites

- **Python 3.10+**
- A reachable **MySQL** database
- **[Ollama](https://ollama.com)** installed and running, with the model pulled:

```bash
ollama pull llama3.2:3b
```

## Setup

```bash
git clone https://github.com/Nanthu-FS/sql-chatbot.git
cd sql-chatbot

python -m venv venv
# Windows
venv\Scripts\activate
# macOS / Linux
source venv/bin/activate

pip install -r requirements.txt
```

## Configuration

Database and model settings are read from environment variables. Copy the example file and fill in your own connection details:

```bash
cp .env.example .env
```

Then edit `.env`:

```env
DB_HOST=<your_db_host>
DB_PORT=<your_db_port>
DB_USER=<your_db_user>
DB_PASSWORD=<your_db_password>
DB_NAME=<your_database_name>
OLLAMA_MODEL=llama3.2:3b
```

> **Note:** `.env` holds your credentials and is gitignored — never commit it. Only `.env.example` (with placeholders) is tracked.

## Running

Make sure Ollama is running and your database is reachable, then:

```bash
streamlit run app.py
```

The app opens at <http://localhost:8501>.

## Usage

1. Type a question about your data (e.g. *"How many orders were placed last month?"*).
2. The assistant generates and runs the SQL, then shows the results.
3. Use the chart toggle to visualize results, or download them as CSV / Excel.

## How it works

1. Your question is passed to a LangChain SQL chain backed by an Ollama LLM.
2. The generated query is cleaned and executed against your database.
3. Results are parsed into a DataFrame and rendered as a table or chart, with export options. Failed queries are automatically retried with a rephrased prompt.

## Security

- Credentials live only in your local `.env` and are never committed.
- All LLM inference runs locally via Ollama — no third-party API keys required.
- Connect with a least-privilege (ideally read-only) database user.
