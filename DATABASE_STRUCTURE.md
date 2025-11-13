# Database Structure Documentation

This document explains how the Medical Quiz app works with SQLite `.db` files.

## Database Schema

### Tables

#### 1. Questions Table
Stores quiz questions with metadata.

```sql
CREATE TABLE Questions (
    id INTEGER PRIMARY KEY,
    question TEXT NOT NULL,
    explanation TEXT,
    corrAns INTEGER NOT NULL,       -- Correct answer ID (1-5)
    title TEXT,
    mediaName TEXT,                  -- Optional media file reference
    otherMedias TEXT,                -- Comma-separated media references
    pplTaken REAL,                   -- Number of people who answered
    corrTaken REAL,                  -- Number who answered correctly
    subId TEXT,                      -- Comma-separated subject IDs
    sysId TEXT                       -- Comma-separated system IDs
)
```

**Example:**
```
id: 1
question: "What is the most common cause of hypothyroidism?"
explanation: "Hashimoto's thyroiditis is an autoimmune disorder..."
corrAns: 2
title: "Endocrine System - Thyroid Disorders"
subId: "3,5"        -- Multiple subjects
sysId: "12"         -- Single system
```

#### 2. Answers Table
Stores answer options for each question.

```sql
CREATE TABLE Answers (
    answerId INTEGER,                -- Answer number (1-5)
    answerText TEXT NOT NULL,
    correctPercentage INTEGER,       -- Optional: % of users who selected this
    qId INTEGER,                     -- Foreign key to Questions.id
    PRIMARY KEY (answerId, qId)
)
```

**Example:**
```
answerId: 1, qId: 1, answerText: "Graves' disease"
answerId: 2, qId: 1, answerText: "Hashimoto's thyroiditis" (CORRECT)
answerId: 3, qId: 1, answerText: "Subacute thyroiditis"
```

#### 3. Subjects Table
Categories/subjects for questions.

```sql
CREATE TABLE Subjects (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    count INTEGER                    -- Number of questions in this subject
)
```

**Example:**
```
id: 1, name: "Cardiology", count: 150
id: 2, name: "Neurology", count: 120
id: 3, name: "Endocrinology", count: 95
```

#### 4. Systems Table
Body systems for questions.

```sql
CREATE TABLE Systems (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    count INTEGER                    -- Number of questions in this system
)
```

**Example:**
```
id: 1, name: "Cardiovascular System", count: 200
id: 2, name: "Nervous System", count: 180
id: 3, name: "Respiratory System", count: 150
```

#### 5. SubjectsSystems Table
Junction table linking subjects and systems with question counts.

```sql
CREATE TABLE SubjectsSystems (
    subId INTEGER,
    sysId INTEGER,
    count INTEGER,                   -- Questions matching both subject and system
    PRIMARY KEY (subId, sysId)
)
```

**Example:**
```
subId: 1 (Cardiology), sysId: 1 (Cardiovascular), count: 145
subId: 2 (Neurology), sysId: 2 (Nervous), count: 110
```

#### 6. logs Table (Created by App)
Tracks user answers and performance.

```sql
CREATE TABLE logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    qid INTEGER,                     -- Question ID
    selectedAnswer INTEGER,          -- Answer selected by user
    corrAnswer INTEGER,              -- Correct answer
    time INTEGER,                    -- Time taken (milliseconds)
    answerDate TEXT,                 -- ISO 8601 timestamp
    testId TEXT                      -- UUID for grouping quiz sessions
)
```

## How the App Works with Database Files

### 1. Database Loading
```kotlin
// User selects a .db file from /storage/emulated/0/MedicalQuiz/databases/
val databaseManager = DatabaseManager(dbPath)
databaseManager.openDatabase()
```

### 2. Querying Questions
```kotlin
// Get all question IDs
val questionIds = databaseManager.getQuestionIds()

// Filter by subject and/or system
val filteredIds = databaseManager.getQuestionIds(
    subjectIds = listOf(1L, 3L),    // Cardiology, Endocrinology
    systemIds = listOf(1L)          // Cardiovascular System
)

// Get specific question
val question = databaseManager.getQuestionById(questionId)
val answers = databaseManager.getAnswersForQuestion(questionId)
```

### 3. Comma-Separated ID Handling
The app handles comma-separated IDs in `subId` and `sysId` fields:

```kotlin
// Database value: subId = "1,3,5"
// Query matches:
// - subId = '1,3,5' (exact match)
// - subId LIKE '1,%' (starts with)
// - subId LIKE '%,1,%' (contains)
// - subId LIKE '%,1' (ends with)
```

### 4. Logging Answers
```kotlin
databaseManager.logAnswer(
    qid = 1,
    selectedAnswer = 2,
    corrAnswer = 2,
    time = 15000,           // 15 seconds
    testId = "uuid-123"
)
```

### 5. Statistics Tracking
```kotlin
// Check question status
val status = databaseManager.getQuestionStatus(qid)
// Returns: UNANSWERED, CORRECT, or INCORRECT

// Get detailed stats
val stats = databaseManager.getQuestionStats(qid)
// Returns: QuestionStats(attempts=5, correct=3, incorrect=2)

// Check history
val everCorrect = databaseManager.hasEverBeenCorrect(qid)
val everIncorrect = databaseManager.hasEverBeenIncorrect(qid)
```

## Data Flow

```
1. User selects database file
   ↓
2. MainActivity → QuizActivity
   ↓
3. DatabaseManager opens SQLite connection
   ↓
4. Load question IDs (optionally filtered)
   ↓
5. For each question:
   - Load Question data
   - Load Answer options
   - Resolve subject/system names
   ↓
6. User selects answer
   ↓
7. Log answer to logs table
   ↓
8. Show explanation and correctness
   ↓
9. Navigate to next question
```

## Media File References

The `mediaName` and `otherMedias` fields reference files in:
```
/storage/emulated/0/MedicalQuiz/media/
```

Example:
- `mediaName: "ecg_001.jpg"`
- `otherMedias: "xray_chest.png,diagram_heart.svg"`

To display media:
```kotlin
val mediaPath = "/storage/emulated/0/MedicalQuiz/media/${question.mediaName}"
// Load and display image using Glide, Coil, or similar
```

## Query Examples

### Get questions by multiple subjects
```sql
SELECT DISTINCT id FROM Questions
WHERE (subId = '1' OR subId LIKE '1,%' OR subId LIKE '%,1,%' OR subId LIKE '%,1')
   OR (subId = '3' OR subId LIKE '3,%' OR subId LIKE '%,3,%' OR subId LIKE '%,3')
ORDER BY id
```

### Get systems for selected subjects
```sql
SELECT s.id, s.name, ss.count
FROM Systems s
JOIN SubjectsSystems ss ON s.id = ss.sysId
WHERE ss.subId IN (1, 3) AND ss.count > 0
GROUP BY s.id, s.name
ORDER BY s.name
```

### Get user performance statistics
```sql
SELECT 
    COUNT(*) as attempts,
    SUM(CASE WHEN selectedAnswer = corrAnswer THEN 1 ELSE 0 END) as correct,
    SUM(CASE WHEN selectedAnswer != corrAnswer THEN 1 ELSE 0 END) as incorrect
FROM logs
WHERE qid = ?
```

## Key Differences from TypeScript/Rust Implementation

### TypeScript (Tauri + SQL Plugin)
- Uses async/await with Tauri SQL plugin
- Database connection via: `Database.load('sqlite:path')`
- Query execution: `db.select<T>(sql, params)`
- Runs in web view with IPC to Rust backend

### Rust (Backend)
- Primarily a path manager, actual queries via Tauri plugin
- Maintains log buffer for performance
- Error handling with `thiserror` crate
- Synchronous API wrapping async operations

### Kotlin (Android)
- Direct SQLite access via `SQLiteDatabase`
- Coroutines for async operations: `withContext(Dispatchers.IO)`
- Native cursor-based queries
- No IPC overhead - direct database access

## Performance Considerations

1. **Connection Pooling**: Keep database open during quiz session
2. **Prepared Statements**: Use parameterized queries
3. **Indexing**: Create index on `logs.qid` for fast statistics
4. **Batch Operations**: Consider transaction batching for multiple logs
5. **Memory**: Close cursors after use to prevent leaks

## Example Database Creation

To create a compatible database:

```sql
-- Create tables
CREATE TABLE Questions (...);
CREATE TABLE Answers (...);
CREATE TABLE Subjects (...);
CREATE TABLE Systems (...);
CREATE TABLE SubjectsSystems (...);

-- Insert sample data
INSERT INTO Subjects VALUES (1, 'Cardiology', 0);
INSERT INTO Systems VALUES (1, 'Cardiovascular System', 0);
INSERT INTO Questions VALUES (
    1, 
    'What is the normal resting heart rate?',
    'Normal adult resting heart rate is 60-100 bpm...',
    2,
    'Basic Cardiology',
    NULL,
    NULL,
    100.0,
    75.0,
    '1',
    '1'
);
INSERT INTO Answers VALUES (1, '40-60 bpm', NULL, 1);
INSERT INTO Answers VALUES (2, '60-100 bpm', NULL, 1);
INSERT INTO Answers VALUES (3, '100-120 bpm', NULL, 1);

-- Update counts
UPDATE Subjects SET count = (SELECT COUNT(*) FROM Questions WHERE subId LIKE '%1%');
UPDATE Systems SET count = (SELECT COUNT(*) FROM Questions WHERE sysId LIKE '%1%');
```

## Testing the Database

Place test `.db` file in:
```
/storage/emulated/0/MedicalQuiz/databases/test_quiz.db
```

Or use ADB:
```bash
adb push test_quiz.db /storage/emulated/0/MedicalQuiz/databases/
```
