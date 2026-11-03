Lenga — Product Requirements Document

Product name: Lenga
Platform: Android
Primary technology: Kotlin + Jetpack Compose
UI framework: Material Design 3
Initial target market: College and TVET students
Future markets: Secondary students, university students, professional learners, working adults, trainers and institutions

Product positioning:

Lenga — Learn. Focus. Unlock.

Core concept:

Lenga helps users turn productive learning into controlled access to distracting phone activities.

1. Executive Summary

Lenga is an Android application that combines AI-powered learning, study planning, assessment, habit formation and application restriction.

The user first identifies their goals, study habits, distractions and preferred level of accountability. Lenga then helps them create a learning plan.

Learning content can come from:

User-uploaded notes, curricula, textbooks and reference material.
Course/unit information supplied by the user.
A combination of both.
AI-generated supplementary material.

Lenga can then generate:

Study notes
Summaries
Learning activities
Practice questions
Quizzes
Revision material
Study plans

During a Lenga session, selected distracting applications can be restricted.

Depending on the chosen mode, the user may need to:

Study → Complete learning activity → Pass a quiz → Unlock selected applications

The user can optionally provide feedback after sessions. This feedback can be used to improve recommendations and personalize future study sessions.

2. Product Vision

Make the phone work for learning instead of working against it.

Lenga should ultimately become a learning accountability platform, rather than simply an application blocker.

The long-term product model is:

GOAL
  ↓
CONTENT
  ↓
PLAN
  ↓
STUDY
  ↓
ASSESS
  ↓
VERIFY
  ↓
UNLOCK
  ↓
FEEDBACK
  ↓
PERSONALIZE
3. Product Principles
3.1 Learning before blocking

Blocking should support learning rather than become the product itself.

3.2 User-controlled accountability

Users choose how strict Lenga should be.

3.3 Content-grounded AI

When users provide learning material, AI should prioritize that material.

3.4 Useful before paid

The free version should provide enough value for users to understand Lenga's benefit.

3.5 Low-friction interaction

Starting a study session should take seconds, not minutes.

3.6 Feedback without annoyance

Session feedback should be optional and lightweight.

3.7 Privacy by design

Application usage and learning information should be handled transparently and minimally.

4. Target Users
Primary MVP
College and TVET students

Typical characteristics:

Smartphone-dependent
Studying multiple subjects/units
Preparing for CATs, exams and practical assessments
Frequently distracted by social media
Need help maintaining consistent study routines
Future users
Secondary school students
University students
Professional certification candidates
Online learners
Working professionals
Self-directed learners
5. User Problems

Lenga addresses:

Digital distraction

Users repeatedly switch from study to entertainment.

Poor consistency

Users struggle to maintain regular study routines.

Procrastination

Users know what they need to do but delay starting.

Passive study

Time spent with study material does not necessarily represent actual learning.

Lack of accountability

There is little immediate consequence for abandoning study.

Exam cramming

Students often begin serious revision too late.

Content fragmentation

Learning material may exist across:

PDFs
Word documents
PowerPoints
Books
Notes
Curricula
Websites

Lenga brings these resources into a structured learning environment.

6. Product Goals

The MVP must allow a user to:

Complete a learning-needs evaluation.
Define learning goals.
Create subjects and units.
Upload learning material.
Enter course/unit information.
Generate learning material using AI.
Generate quizzes using AI.
Generate study plans.
Start focus sessions.
Restrict selected applications.
Complete study requirements.
Complete quizzes.
Answer selected questions by typing or voice.
Receive controlled unlocks.
Track progress.
Provide optional session feedback.
7. Non-Goals for MVP

Lenga will not initially attempt to be:

A full LMS
A school management system
A social network
A replacement for Moodle/Google Classroom
A complete AI tutor
A teacher grading system
A parental surveillance platform

These may become future products/features.

8. Initial User Journey
INSTALL LENGA
      ↓
ONBOARDING
      ↓
LEARNER EVALUATION
      ↓
DEFINE GOALS
      ↓
CREATE SUBJECT / UNIT
      ↓
ADD LEARNING CONTENT
      ↓
GENERATE PLAN
      ↓
SELECT LENGA MODE
      ↓
START STUDY
      ↓
COMPLETE REQUIREMENTS
      ↓
QUIZ / ASSESSMENT
      ↓
PASS
      ↓
UNLOCK
      ↓
SESSION FEEDBACK
      ↓
PROGRESS
9. Learner Evaluation

The initial evaluation should take approximately 3–5 minutes.

It should not feel like a psychological test. Its purpose is to understand the learner's practical needs.

9.1 Learner Profile

Collect:

Education level
Institution type
Course/programme
Year/level
Subjects/units

Education options:

Certificate
Diploma
Degree
Professional certification
Other

Institution options:

TVET
University
College
Online
Other
10. Goals Evaluation

Question:

What are your main reasons for using Lenga?

Multi-select:

Prepare for exams
Improve grades
Complete assignments
Keep up with coursework
Prepare for practical assessments
Learn a difficult subject
Develop a new skill
Reduce phone distractions
Build a study habit
Avoid last-minute studying
Prepare for professional certification
Other

The user can then define a specific goal.

Example:

Goal:
Pass Web Development

Target:
70%

Target date:
18 October 2026
11. Study Habits Evaluation

Collect:

Daily study time
Less than 30 minutes
30–60 minutes
1–2 hours
2–4 hours
More than 4 hours
Study frequency
Almost every day
4–5 days/week
2–3 days/week
Mostly before exams
Very irregular
Preferred study time
Early morning
Morning
Afternoon
Evening
Late night
Varies
Preferred location
Home
Library
Campus
Hostel
Workplace
Anywhere
12. Distraction Evaluation

Ask:

What most often interrupts your study?

Multi-select:

WhatsApp
TikTok
Instagram
Facebook
YouTube
Games
Streaming
Web browsing
Messaging
Notifications
Phone calls
Other

Then:

What normally happens when you intend to study?

I start scrolling social media.
I watch videos.
I respond to messages.
I play games.
I browse unrelated websites.
I repeatedly check notifications.
I procrastinate before starting.
I switch between apps.
I generally stay focused.
13. Essential Apps

The user must identify applications that are necessary for studying.

Examples:

Chrome
PDF reader
Calculator
YouTube
WhatsApp
Google Drive
Microsoft Office
AI tools
Dictionary
LMS
Email
Camera
Notes
Music

These become the user's Allow List.

14. Accountability Level

The user chooses how strongly Lenga should intervene.

Level 1 — Gentle

Reminders only.

Level 2 — Guided

Focus sessions and encouragement.

Level 3 — Restricted

Selected distracting apps are blocked during study.

Level 4 — Strict

Apps remain blocked until configured requirements are completed.

Level 5 — Challenge

The user must complete learning activities and/or quizzes before unlocking selected apps.

15. Recommendation Engine

Lenga analyses:

Goals
+
Study habits
+
Distraction pattern
+
Academic urgency
+
Available study time
+
Accountability preference
+
Essential apps

It then proposes a study approach.

Example:

Your recommended plan

60-minute study sessions
+
10-question quizzes
+
70% pass requirement
+
Social media blocked during sessions
+
30-minute entertainment unlock

The user can:

Accept Plan

or

Customize Plan

The recommendation is never forced.

16. Learning Content System

Content is a first-class component of Lenga.

Users have three primary options.

Option A — Upload material

Supported materials should eventually include:

PDF
DOC/DOCX
PPT/PPTX
TXT
Images/scanned notes where OCR is available

Potential sources:

Notes
Curricula
Syllabi
Reference books
Handouts
Trainer material
Revision material
Past papers
Option B — Enter course/unit information

Example:

Course:
Diploma in ICT

Unit:
Web Design

Level:
Diploma

Topics:
HTML
CSS
JavaScript
Forms
DOM

AI generates appropriate material from this information.

Option C — Hybrid

The preferred option.

Example:

Diploma in ICT
        +
Web Design Curriculum.pdf
        +
Trainer Notes.docx

AI uses supplied material as the primary source.

17. Content Source Priority

When generating learning content:

1. User-uploaded curriculum
2. User-uploaded notes
3. User-uploaded reference material
4. User-provided course/unit information
5. AI-generated supplementary content

AI-generated supplementary material must be distinguishable from user-provided source material.

18. Learning Library

The application must provide a persistent learning library.

Example:

My Learning Library

Web Design
 ├── Curriculum
 ├── Trainer Notes
 ├── Study Notes
 ├── Quizzes
 └── Generated Material

Database Systems
 ├── Curriculum
 ├── Notes
 └── Quizzes

Users should be able to:

Add material
Remove material
Rename material
Organize subjects
Organize units
Regenerate content
View generated content
19. AI Content Engine

The AI engine should generate:

Study notes

Structured learning material based on the selected unit.

Summaries

Concise revision material.

Explanations

Explanations at the learner's level.

Examples

Relevant examples and demonstrations.

Key concepts

Important concepts extracted from the source.

Flashcards

Question/answer revision cards.

Practice questions

Questions that don't necessarily affect unlocking.

Quizzes

Formal learning verification.

Study plans

Schedules based on:

Goal
Exam date
Topics
Available time
Current progress
20. Content Processing Pipeline
Upload
   ↓
Extract
   ↓
Clean
   ↓
Chunk
   ↓
Analyse
   ↓
Index
   ↓
Knowledge Base
   ↓
Generate
   ↓
Study Material / Questions / Quizzes

The implementation should be designed so the content-processing provider can be changed later without redesigning the Android UI.

21. Core Lenga Modes
Mode 1 — Focus Session

No learning content is required.

User chooses:

Duration: 60 minutes

Blocked:
TikTok
Instagram
Games

Allowed:
Chrome
PDF Reader
Calculator
22. Mode 2 — Study → Unlock

User must complete a study duration.

Example:

Web Development

Required:
45 minutes

Progress:
32 / 45 minutes

Upon completion:

Entertainment unlocked for 30 minutes.

23. Mode 3 — Quiz → Unlock

Requires learning content.

Example:

10 questions
Pass mark: 70%

User passes:

Unlock earned.

User fails:

Review and retry.

24. Mode 4 — Study + Quiz → Unlock

This is the central Lenga mode.

Study: 60 minutes ✓

Quiz: 10 questions
Score: 70% required

Status:
○ Quiz pending

All configured requirements must be completed before unlocking.

25. Mode 5 — Daily Study Target

Example:

Today's target

2 hours

Session 1: 45 min ✓
Session 2: 35 min ✓
Session 3: 40 min

Remaining: 40 min

Multiple sessions can contribute toward the same target.

26. Mode 6 — Exam Preparation

User supplies:

Subject
Exam date
Target score
Topics
Available study time
Available study days

Lenga generates a schedule.

Example:

Week 1
HTML + CSS

Week 2
JavaScript

Week 3
DOM + Forms

Week 4
Revision + Mock Test
27. Mode 7 — Deep Work

Long uninterrupted sessions.

Example:

Duration: 3 hours

Block:
Social media
Games
Entertainment
Messaging

Allow:
Study apps
Browser
Calculator
Emergency functions
28. Mode 8 — Habit Building

Example:

Daily minimum:
30 minutes

Mon ✓
Tue ✓
Wed ✓
Thu ✓
Fri ✓

Streak:
5 days
29. Future Mode — Assignment

Example:

Assignment:
Database Management System

Deadline:
Friday

Study requirement:
90 minutes

This mode can later integrate with LMS systems.

30. Assessment Engine

The assessment engine supports:

Multiple choice

Single correct answer.

Multiple-answer

Several correct answers.

True/false

Objective statements.

Written response

Learner writes their response.

Dictated response

Learner speaks their response.

31. Typed and Voice Responses

Phase 6 must support:

Question
   ↓
Choose response method
   ├── Type
   └── Dictate
             ↓
        Speech-to-text
             ↓
        Review transcript
             ↓
           Submit

The user must be able to edit the generated transcription before submission.

Example:

Your response:

"An ID identifies a specific element..."

[Edit]       [Submit]
32. Open-Ended Answer Evaluation

AI can evaluate suitable written responses.

Example:

Question:
Explain two advantages of OOP.

Response:
"Objects allow code to be reused..."

Evaluation:

Key concepts:
✓ Reusability
✓ Encapsulation

Score:
8/10

Feedback:
Your answer identifies two relevant concepts...

AI evaluation should use an explicit rubric where possible.

Objective questions should use deterministic checking instead of AI evaluation where practical.

33. Quiz Configuration

Users can configure:

Number of questions
Difficulty
Topics
Question types
Pass mark
Time limit

Example:

Questions: 10
Difficulty: Medium
Pass mark: 70%
Time: 15 minutes
34. Focus and Blocking Engine

The blocking engine manages:

Active sessions
Blocked applications
Allowed applications
Temporary unlocks
Emergency unlocks
Session state

Android permissions and OEM-specific behaviour must be handled carefully.

The application must provide a clear explanation before requesting required permissions.

35. Blocking Screen

When a blocked application is opened:

Stay Focused

Instagram is currently locked.

Complete your Lenga requirement
to unlock it.

Progress:
42 / 60 minutes

[Back to Study]

[View Progress]

[Emergency Unlock]

The interface should be supportive rather than punitive.

36. Unlock System

Unlocks can be:

Time based
5 minutes
15 minutes
30 minutes
60 minutes
120 minutes
Application based

Specific apps.

Category based
Social media
Games
Entertainment
Temporary unrestricted access

Future feature.

37. Emergency Unlock

Users must have a controlled escape mechanism.

Example:

Emergency Unlock

5 minutes
10 minutes
15 minutes

Emergency unlocks are logged.

Emergency unlocks this week: 3

Essential/emergency functions should remain accessible according to Android capabilities and user configuration.

38. Dashboard

The home screen should show:

Good afternoon

Today's progress

1h 20m / 2h
████████████░░░░

Next session
Web Development
45 minutes

Current streak
5 days

Today's requirements
✓ Study session
○ Quiz
○ Daily target

Primary CTA:

Start Study Session

39. Study Session Screen
Web Development

Focus Session

42:18

████████████░░

Blocked apps: 3
Allowed apps: 5

[Pause]
[End Session]

The active study screen should be intentionally minimal.

40. Quiz Screen
Question 4 of 10

Which technology is used to
style HTML documents?

○ JavaScript
○ CSS
○ SQL
○ Kotlin

[Next]
41. Quiz Result
Your Score

8 / 10

80%

Required:
70%

✓ Requirement completed

[Unlock Apps]

If unsuccessful:

60%

Required:
70%

Review recommended topics:

• CSS selectors
• CSS properties

[Review]
[Try Again]
42. Progress Dashboard

Track:

Study time
Completed sessions
Quiz scores
Goals
Streak
Unlock time
Emergency unlocks
Blocked-app attempts
Daily/weekly performance

Example:

THIS WEEK

Study time       8h 40m
Sessions         12
Quiz average     78%
Goals completed  4
Streak           6 days
43. Session Feedback

Feedback is optional.

Immediately after a session:

Session Complete ✓

How was your session?

😫   😕   😐   🙂   🤩

[Done]

If the user chooses to provide more information:

What went well?
I stayed focused
The material was useful
The quiz was helpful
The session length was good
The content was easy to understand
Blocking helped me
I achieved my goal
What could be improved?
Session too long
Session too short
Content too difficult
Content too easy
Quiz too difficult
Quiz too easy
Questions didn't match material
Blocking too strict
Blocking not strict enough
Technical problem
Need more explanation

Optional free-text:

Anything else you'd like us to know?

44. Feedback Data

Feedback should be associated with:

Session
Subject
Unit
Mode
Duration
Content source
Quiz
Quiz score
Blocking events
User rating
Feedback tags
Comments

This allows Lenga to identify patterns.

45. Personalization

Feedback can influence future recommendations.

For example:

"60 minutes is too long."

Lenga may suggest:

Try 35-minute sessions next week.

Similarly:

"Quiz questions are too easy."

may result in:

Increase quiz difficulty.

Recommendations should be presented to the user rather than silently changing their preferences.

46. Notifications

Support:

Study reminders

Your Web Development session starts in 15 minutes.

Session completion

Study session completed.

Quiz reminder

Your quiz is waiting.

Goal progress

You're 75% through this week's target.

Notifications should be configurable and should not themselves become distracting.

47. Technical Architecture

Recommended architecture:

Lenga
│
├── app
│
├── core
│   ├── ui
│   ├── model
│   ├── database
│   ├── network
│   ├── permissions
│   └── utilities
│
├── feature
│   ├── onboarding
│   ├── evaluation
│   ├── dashboard
│   ├── library
│   ├── content
│   ├── goals
│   ├── plans
│   ├── focus
│   ├── quiz
│   ├── blocking
│   ├── progress
│   ├── feedback
│   └── settings
│
└── service
    ├── blocking
    ├── session
    ├── content
    ├── notification
    └── ai
48. Android Technology Stack

Use:

Kotlin
Android Studio
Jetpack Compose
Material Design 3
Navigation Compose
ViewModel
Kotlin Coroutines
Flow / StateFlow
Room
DataStore
WorkManager where appropriate
Android-supported application usage/accessibility mechanisms for restriction functionality
Android Speech Recognition APIs or an appropriate speech-to-text service
Secure network communication for AI services
49. Architecture Pattern

Use a clean, modular architecture:

Compose UI
    ↓
ViewModel
    ↓
Use Case
    ↓
Repository
    ↓
Local / Remote Data

Example:

StudySessionScreen
       ↓
StudySessionViewModel
       ↓
StartStudySessionUseCase
       ↓
StudySessionRepository
       ↓
Room
50. AI Architecture

The Android application should not contain provider-specific AI logic throughout the UI.

Use an abstraction such as:

AIService
 ├── generateStudyPlan()
 ├── generateNotes()
 ├── generateQuestions()
 ├── generateQuiz()
 ├── evaluateResponse()
 └── summarizeMaterial()

This allows the AI provider to be changed later.

For production, sensitive AI credentials should not be embedded directly in the Android application.

51. Content Data Model

Core entities:

User
Goal
Subject
Unit
LearningSource
Topic
GeneratedContent
Question
Quiz
QuizAttempt
StudyPlan
Requirement
StudySession
BlockedApp
AllowedApp
Unlock
EmergencyUnlock
SessionFeedback
52. Example LearningSource
LearningSource
- id
- unitId
- type
- filename
- title
- processingStatus
- sourceMetadata
- createdAt

Types:

PDF
DOCX
PPTX
TXT
IMAGE
COURSE_INFORMATION
AI_GENERATED
53. Example GeneratedContent
GeneratedContent
- id
- unitId
- sourceIds
- type
- title
- content
- generatedAt
- version

Content types:

NOTE
SUMMARY
EXPLANATION
FLASHCARD
PRACTICE_SET
QUIZ
STUDY_PLAN
54. Example SessionFeedback
SessionFeedback
- id
- sessionId
- rating
- positiveTags
- improvementTags
- comment
- createdAt
55. Material Design 3

Use:

Scaffold
NavigationBar
TopAppBar
Card
ElevatedCard
FilterChip
AssistChip
LinearProgressIndicator
CircularProgressIndicator
ModalBottomSheet
AlertDialog
Switch
Slider
Checkbox
RadioButton
Button
FloatingActionButton

Support:

Light theme
Dark theme
Dynamic color
Accessibility font scaling
Screen-reader-friendly controls
56. Primary Navigation

Bottom navigation:

Home
Plans
Focus
Progress
Settings

The Learning Library can be accessible from Home/Plans or as a secondary navigation destination.

Quiz should normally be launched contextually from a learning requirement.

57. Privacy

Lenga will potentially process:

Application usage information
Learning behaviour
Uploaded documents
Quiz responses
Voice transcriptions
Learning goals

Therefore:

Clearly explain required permissions.
Minimize collected data.
Allow users to delete their data.
Clearly disclose AI processing.
Secure uploaded learning material.
Do not sell personal learning/activity information.
Clearly distinguish local data from cloud-processed data.
Provide appropriate controls for voice data.
Never silently record conversations.
58. Monetization Strategy

Lenga should use a freemium model.

Lenga Free

Potential features:

Focus sessions
Basic app blocking
Basic study plans
Limited active plans
Basic quizzes
Limited AI generation
Basic progress
Session feedback
59. Lenga Plus

Indicative starting hypothesis:

KSh 299–499/month

or approximately:

KSh 2,499–3,999/year

Potential features:

Unlimited/custom study plans
Upload learning material
AI-generated notes
AI-generated quizzes
Larger AI allowance
Exam preparation
Advanced blocking
Voice responses
Advanced analytics
Multiple subjects/units

Prices should be validated through market testing before final implementation.

60. Lenga Pro

Potential target:

University students
Professional learners
Certification candidates
Working professionals

Potential features:

Everything in Plus
Large document libraries
AI tutor
Adaptive learning
Advanced assessment
Multiple courses
Higher AI allowance
Priority processing

Indicative starting hypothesis:

Around KSh 699/month

Again, this should be validated rather than treated as a fixed price.

61. AI Usage Limits

Because AI processing has a variable cost, subscriptions should have usage allowances.

Example:

Free
50 AI questions/month
3 document analyses/month
3 generated study plans/month
Plus
500 AI questions/month
25 document analyses/month
Higher generation limits
Pro

Higher limits appropriate for intensive learners.

The exact limits should be adjusted according to actual AI costs.

62. Trainer Edition

Future product:

Lenga for Trainers

Features:

Trainer accounts
Course management
Unit management
Learning-material upload
AI-generated study material
Quiz generation
Student assignments
Student progress
Class dashboards

Potential pricing:

Free
Trainer
Professional
Institution

Indicative trainer pricing:

KSh 1,500–5,000/month, depending on student/class limits.

63. Institution Edition

Future institutional offering:

Lenga for TVETs & Colleges

Features:

Institution dashboard
Departments
Courses
Units
Trainers
Students
Learning material
Assessments
Progress analytics
Administrative reporting

Pricing should be custom based on:

Number of students
Number of trainers
AI usage
Storage
Reporting requirements
64. Content Marketplace

Future possibility:

Trainers can sell:

Revision packs
Course notes
Quizzes
Flashcards
Exam preparation packs

Example:

Diploma ICT — Web Design Revision Pack
KSh 200

Lenga can take a platform fee.

This should be considered only after the core product and content ecosystem are established.

65. Advertising

Advertising should not be a primary monetization mechanism.

Lenga's value proposition is reducing digital distraction. Interrupting a study session with advertising would conflict with the product.

If advertising/sponsorship is eventually introduced, it should:

Not appear during active study sessions.
Be non-intrusive.
Be clearly identified.
Never interfere with learning.
66. Monetization Roadmap
Stage 1

Free + Plus

Validate student demand.

Stage 2

AI usage monetization

Determine whether users will pay for AI-generated learning content.

Stage 3

Trainer subscriptions

Allow educators to manage learning content and students.

Stage 4

Institution licensing

Sell Lenga to TVETs, colleges and universities.

Stage 5

Content marketplace

Enable educators to monetize learning resources.

MVP Scope — Release 1

This section defines the first release. Where it conflicts with sections 67–78, this section takes priority for Release 1. Sections 67–78 describe the full MVP that later releases build towards.

R1.1 Purpose

Release 1 is the smallest product that tests whether Lenga's core loop works for real students:

Add unit
   ↓
Generate quiz
   ↓
Study (apps blocked)
   ↓
Pass quiz
   ↓
Unlock

Everything not needed to test this loop is deferred.

R1.2 Hypotheses to validate

1. Students will let Lenga block their distracting apps during study and keep blocking switched on.
2. AI quizzes generated from a student's unit information or notes are accurate enough to decide whether apps unlock.
3. Study + Quiz → Unlock makes students come back and study repeatedly.

R1.3 Starting point

Release 1 is built on the existing StudyLock Android codebase, renamed to Lenga.

Already working in StudyLock:

Blocking distracting apps with an accessibility service
Essential apps that are never blocked
Time-based unlocks that survive restarts and expire automatically
Quiz → Unlock with reward time
PIN-protected emergency unlock, limited per day and logged
Progress: study time, streak, weekly chart
Focus modes (timed, detox day, weekly schedule, one-off), in progress

Release 1 replaces StudyLock's hardcoded study modules and questions with user-created subjects, units and AI-generated quizzes.

R1.4 In scope

Onboarding (target: under 2 minutes)

Course/programme and education level
Distracting apps (block list)
Essential apps (allow list)
Permission explanations, then permission requests
Emergency PIN

The full learner evaluation (sections 9–12) and the recommendation engine (section 15) are replaced by sensible defaults:

45-minute study requirement
10-question quiz
70% pass mark
30-minute unlock

Account

Sign-in is required before using AI features, so AI usage can be limited per user. Blocking and focus sessions work without an account.

Subjects and units

Create, rename and delete subjects.
Create, rename and delete units within a subject.

Learning content

Option B — Course/unit information: course, unit, level and a list of topics.
Option A — PDF upload: text-based PDFs only.
Hybrid: both can be added to the same unit; uploaded material takes priority (section 17).

AI generation

Quiz question pool per unit (multiple choice and true/false).
One short summary per unit, shown before the quiz.

Questions based only on course/unit information, not uploaded material, are labelled as AI-generated (section 17).

Lenga modes

Focus Session (section 21)
Study + Quiz → Unlock (section 24), the central mode

Study → Unlock and Quiz → Unlock are not separate modes in Release 1. They are Study + Quiz → Unlock with the quiz switched off or the study time set to zero.

Focus modes (timed, detox day, weekly schedule, one-off) are included because they are already implemented in StudyLock.

Strictness

The five accountability levels (section 14) are reduced to two settings:

Blocked during session — apps unlock when the session ends.
Blocked until complete — apps stay blocked until every requirement is met.

Quiz rules

Answers are checked on the device; no AI is used for marking.
Each attempt draws a new set of questions from the unit's pool, with answer options shuffled.
After a failed attempt, the learner sees the topics they missed and must wait 5 minutes before retrying.

Unlocks

Time-based unlock earned by completing requirements.
Emergency unlock (section 37), as already implemented.

Progress

Study time
Completed sessions
Quiz scores
Streak
Emergency unlocks
Blocked-app attempts

Feedback

After each quiz, one optional question:

Did the questions match your material?

Yes / Partly / No

This measures hypothesis 2 directly. The full feedback system (sections 43–45) is deferred.

R1.5 Out of scope for Release 1

Deferred feature                                  Earliest phase
Full learner evaluation and recommendation engine  Phase 7
Study plans, exam preparation, daily targets       Phase 2
Habit building mode                                Phase 5
DOCX, PPTX, TXT and image/OCR upload               Phase 1 (next release)
Study notes, flashcards, explanations              Phase 1 (next release)
Multiple-answer, written and voice responses       Phase 6
AI evaluation of open-ended answers                Phase 6
Personalization and full session feedback          Phase 7
Study reminders and goal notifications             Phase 3
Subscriptions and payments                         After Release 1 pilot
Trainer and institution editions                   Phases 9–10

Release 1 is free, with fixed AI usage limits.

R1.6 Technical decisions

Application

Kotlin, Jetpack Compose, Material Design 3 (section 48).
Single app module with feature packages; split into modules only when build times or team size require it.
Room for learning data (subjects, units, sources, questions, quiz attempts, sessions).
DataStore for settings and focus modes.
Application ID changed from com.example.studylock to a Lenga ID before any public release.

PDF processing

Text is extracted from PDFs on the device. Only the extracted text is sent for AI processing; the PDF itself is not uploaded or stored on a server.

This reduces mobile data use and keeps the learner's files on their phone.

Backend

A small backend provides:

Authentication
An AI proxy, so AI credentials are never in the app (section 50)
Per-user AI usage limits

Recommended: Supabase (Auth + Edge Functions). The app talks to the backend only through the AIService interface (section 50), so the backend and AI provider can be changed later.

Offline use

Generation needs a network connection. Everything else works offline:

Blocking and focus sessions
Studying with saved summaries
Quizzes from a saved question pool
Progress

Each generation creates a pool of about 30 questions per unit, so retries do not need the network and AI cost stays low.

AI usage limits (initial, adjust after measuring cost)

5 quiz-pool generations per month
3 PDF analyses per month

R1.7 Play Store readiness

Prominent disclosure screen before the accessibility permission is requested, explaining exactly what it is used for.
Accessibility API declaration in the Play Console.
Privacy policy covering app usage data, uploaded text and AI processing (section 57).
Data safety form.
In-app deletion of account and data.

Fallback: if the accessibility declaration is rejected, blocking moves to usage access (UsageStats) with an overlay. The blocking engine should keep its decision logic separate from how the foreground app is detected, so this switch does not affect the rest of the app.

R1.8 Release 1 Definition of Done

Install Lenga
      ↓
Complete short onboarding
      ↓
Select distracting and essential apps
      ↓
Grant permissions
      ↓
Create subject and unit
      ↓
Enter unit topics and/or upload a PDF
      ↓
Generate quiz pool and summary
      ↓
Start Study + Quiz session
      ↓
Attempt to open blocked app → Lenga blocks it
      ↓
Complete study time
      ↓
Take quiz
      ↓
Fail → review missed topics → retry after 5 minutes
      ↓
Pass → unlock earned
      ↓
Use unlocked apps
      ↓
Apps lock again when unlock time ends
      ↓
Rate quiz match (optional)
      ↓
View progress

All of this must work on at least one Xiaomi/MIUI device and one Samsung device, including after a phone restart.

R1.9 Pilot and success criteria

Release 1 is tested with a pilot group of 20–30 TVET or college students over 2 weeks, before any public launch.

Proposed targets:

Activation: 60% of users complete their first Study + Quiz session.
Engagement: 3 or more sessions per user per week in week 2.
Quiz quality: 80% of quiz ratings are "Yes" (questions matched the material).
Blocking kept on: 70% of users still have blocking enabled at the end of week 2.
Emergency unlocks: fewer than 1 per user per day on average.

If quiz quality is below target, improving generation comes before any new features.

R1.10 Open decisions

Sign-in method: email, Google or phone number.
AI provider and model, chosen on measured cost per question pool.
Pilot institution(s).
Final application ID and Play Store listing name.

67. MVP Development Roadmap
Phase 1 — Foundation + Learning Content
Android foundation
Kotlin
Compose
Material 3
Navigation
Room
DataStore
ViewModels
Coroutines/Flow
User foundation
Onboarding
Evaluation
Profile
Goals
Subjects
Units
Learning Library
Create subject
Create unit
Upload files
View files
Delete files
Organize content
AI content
Course/unit-based generation
Document-based generation
Hybrid generation
Study notes
Summaries
Questions
Quizzes
Study-plan generation
68. Phase 2 — Study Planning

Implement:

Study plans
Daily targets
Study schedules
Requirements
Plan customization
Goal tracking
69. Phase 3 — Focus Engine

Implement:

Focus timer
Study sessions
Session state
Notifications
Study-time tracking
70. Phase 4 — App Restriction

Implement:

Permission onboarding
Application detection
Block list
Allow list
Blocking screen
Unlock state
Emergency unlock
71. Phase 5 — Core Lenga Modes

Implement:

Study → Unlock
Quiz → Unlock
Study + Quiz → Unlock
Daily Study Target
Deep Work
Habit Building
72. Phase 6 — Advanced Assessment

Implement:

Multiple choice
True/false
Multiple-answer
Typed responses
Voice/dictated responses
Speech-to-text
Editable transcription
AI evaluation of open-ended responses
Rubrics
Feedback/explanations
Adaptive quizzes
73. Phase 7 — Feedback & Personalization

Implement:

Post-session feedback
Content feedback
Quiz feedback
Blocking feedback
Session analytics
Personalized recommendations
Adaptive session duration
Adaptive difficulty
74. Phase 8 — Advanced AI

Implement:

AI tutor
Weak-topic detection
Adaptive revision
Advanced exam preparation
AI-generated learning paths
Intelligent question generation
Personalized explanations
75. Phase 9 — Trainer Platform

Implement:

Trainer accounts
Courses
Units
Trainer content
Trainer quizzes
Student assignments
Student progress
Class dashboards
76. Phase 10 — Institution Platform

Implement:

Institution accounts
Departments
Courses
Trainers
Students
Institutional analytics
Administration
Licensing/billing
77. MVP Success Metrics
Activation

Percentage of users who:

Complete evaluation → create first plan → start first session.

Engagement
Sessions/user/week
Study minutes/user
Quiz attempts
Goals completed
Retention
Day 1
Day 7
Day 30
Learning
Quiz completion
Average quiz score
Study consistency
Goal completion
Blocking
Blocked-app attempts
Session completion rate
Emergency unlock frequency
AI
Content generation requests
Generated-content usage
User ratings of AI content
Quiz quality feedback
Monetization
Free → Plus conversion
Monthly recurring revenue
Annual subscriptions
AI cost/user
Revenue/user
Churn
78. MVP Definition of Done

A first production MVP is functional when a new user can:

Install Lenga
      ↓
Complete evaluation
      ↓
Create goal
      ↓
Create subject/unit
      ↓
Upload notes OR provide course details
      ↓
Generate learning content
      ↓
Generate study plan
      ↓
Select distracting apps
      ↓
Select essential apps
      ↓
Grant required permissions
      ↓
Start study session
      ↓
Attempt to open blocked application
      ↓
Lenga restricts access
      ↓
Complete study requirement
      ↓
Take generated quiz
      ↓
Pass required score
      ↓
Receive unlock
      ↓
Use selected applications
      ↓
Apps become restricted again
      ↓
Provide optional feedback
      ↓
View progress
79. Core Product Architecture

The final Lenga concept can be represented as five connected engines:

┌──────────────────────────────┐
│       1. LEARNING ENGINE     │
│                              │
│ Notes • Sources • AI Content │
└──────────────┬───────────────┘
               ↓
┌──────────────────────────────┐
│       2. PLANNING ENGINE     │
│                              │
│ Goals • Plans • Requirements │
└──────────────┬───────────────┘
               ↓
┌──────────────────────────────┐
│       3. ASSESSMENT ENGINE   │
│                              │
│ Quizzes • Voice • Responses  │
└──────────────┬───────────────┘
               ↓
┌──────────────────────────────┐
│       4. FOCUS ENGINE        │
│                              │
│ Sessions • Blocking • Timer   │
└──────────────┬───────────────┘
               ↓
┌──────────────────────────────┐
│       5. REWARD ENGINE       │
│                              │
│ Unlocks • Rewards • Progress │
└──────────────┬───────────────┘
               ↓
          FEEDBACK
               ↓
       PERSONALIZATION
80. Final Product Loop

The fundamental Lenga experience should remain extremely simple:

       ┌───────────────┐
       │   SET GOAL    │
       └───────┬───────┘
               ↓
       ┌───────────────┐
       │ GET CONTENT   │
       └───────┬───────┘
               ↓
       ┌───────────────┐
       │ CREATE PLAN   │
       └───────┬───────┘
               ↓
       ┌───────────────┐
       │     STUDY     │
       └───────┬───────┘
               ↓
       ┌───────────────┐
       │   ASSESS      │
       └───────┬───────┘
               ↓
       ┌───────────────┐
       │    VERIFY     │
       └───────┬───────┘
               ↓
       ┌───────────────┐
       │    UNLOCK     │
       └───────┬───────┘
               ↓
       ┌───────────────┐
       │   FEEDBACK    │
       └───────┬───────┘
               ↓
       ┌───────────────┐
       │ PERSONALIZE   │
       └───────────────┘
Final positioning

Lenga should not be built or marketed merely as an app blocker.

Its fundamental value proposition is:

Lenga transforms learning into a prerequisite for digital rewards.

The Android blocking capability provides the accountability mechanism.
The AI/content system provides the learning material.
The assessment system provides verification.
The unlock system provides the immediate reward.
The feedback system provides continuous personalization.