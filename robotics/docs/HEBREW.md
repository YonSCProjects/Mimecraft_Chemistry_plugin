# RoboCraft - Hebrew strings, for proofreading

Every piece of player-facing Hebrew in the plugin, grouped by where it appears.

**It was written by a non-native speaker and has never been read by one.** Wording,
gender forms and register all need a pass before this meets a class. ChemCraft's
convention of slashed dual-gender forms (`עוזר/ת`) is not applied consistently here yet.

Latin tokens inside a string - `S1`, `A1`, `ON`, `IF`, `/rc trace` - are deliberate and
must stay Latin: a rule row has to read identically in any client.


285 strings, plus 280 mission strings and 15 code strings added 2026-09-10, and 14 for the
teacher's pause added 2026-09-11, and the mission card's words plus 31 rewritten or new mission lines
added 2026-09-13 - see the last three sections.


## Onboarding and the guide

| Where | Text |
|---|---|
| `Guide` | 1. הניחו בקר. כל רכיב שתניחו לידו מתחבר אליו ומקבל שם: S1, S2 לחיישנים, A1 למפעילים. |
| `Guide` | 2. חייבת להיות סוללה. בלי אנרגיה הרובוט לא רץ. |
| `Guide` | 3. לחיצה ימנית על הבקר פותחת את התוכנית - שורה אחת לכל כלל. |
| `Guide` | 4. הכללים רצים מלמעלה למטה בכל סיבוב. כלל מאוחר גובר על מוקדם. |
| `Guide` | 5. פלט זוכר את מצבו! נורה שנדלקה לא תיכבה לבד - צריך כלל שמכבה אותה. |
| `Guide` | 6. התוויות מעל הרכיבים מראות מה כל חיישן קורא עכשיו - שם מנפים באגים. |
| `Guide` | 7. /rc missions - רשימת המשימות. הרצה בודקת את הרובוט ואומרת מה לא עבד. |
| `Guide` | 8. תקועים? /rc trace מראה מה כל חיישן קורא ואיזה כלל קבע כל פלט. |
| `Guide` | 9. עדיין תקועים? שאלו: /rc ask <שאלה> - התשובה תגיע אליכם בצ'אט. |
| `Guide` | ==== RoboCraft - איך משחקים ==== |
| `Guide` | IF S1 < 7 THEN A1 ON  =  כשהאור קטן מ-7, הדליקו את A1. |
| `Guide` | ·  רכיבים: |
| `Guide` | ברוכים הבאים ל-RoboCraft! זו הסדנה שלכם, והקיר שלפניכם הוא מלאי הרכיבים. |
| `Guide` | הבאה בתור: |
| `Guide` | המשימה הראשונה: /rc missions | המדריך: /rc guide | שאלה: /rc ask |
| `Guide` | הניחו בקר, סוללה, חיישן ומפעיל קרוב זה לזה - ואז לחצו על הבקר וכתבו כלל. |
| `Guide` | חשים, מחליטים, פועלים |
| `Guide` | כל מנגנון רובוטי הוא לולאה: חיישן נותן מספר, התוכנית מחליטה, המפעיל פועל. |
| `Guide` | משימות: |
| `Guide` | פקודות: /rc guide | /rc kit | /rc tp | /rc missions | /rc trace | /rc ask | /rc charge |
| `StarterKit` | קיבלתם ערכת פתיחה - בקר, סוללה, חיישן ומפעיל. |
| `WorkshopKiosk` | \nלחיצה ימנית טוענת רובוטים בסביבה |
| `WorkshopKiosk` | עמדת טעינה |

## Parts and their labels

| Where | Text |
|---|---|
| `parts` | 1 אם יש שחקן בקרבת מקום |
| `parts` | 1 כשיורד גשם |
| `parts` | בקר |
| `parts` | גשר מהאבן האדומה אל התוכנית |
| `parts` | האנרגיה נגמרת. רובוט בלי חשמל נעצר |
| `parts` | המוח. לחיצה ימנית פותחת את התוכנית |
| `parts` | הצבע של הבלוק שמתחתיו |
| `parts` | זמזם |
| `parts` | חום מאש ולבה, קור מקרח ושלג |
| `parts` | חיישן אבן אדומה |
| `parts` | חיישן אור |
| `parts` | חיישן גשם |
| `parts` | חיישן חום |
| `parts` | חיישן מרחק |
| `parts` | חיישן נוכחות |
| `parts` | חיישן צבע |
| `parts` | טוען את הסוללה לפי עוצמת האור |
| `parts` | לוח סולארי |
| `parts` | מציג מספר או קריאה של חיישן - הכלי לניפוי באגים |
| `parts` | מצפצף כשהוא נדלק |
| `parts` | מרחק לבלוק הראשון בכיוון שאליו הוא מכוון |
| `parts` | משואה |
| `parts` | נדלקת ונכבית. זוכרת את מצבה עד שתגידו אחרת |
| `parts` | נורה |
| `parts` | נפתח ונסגר - מפעיל מכני |
| `parts` | סוללה |
| `parts` | עוצמת האור במקום החיישן |
| `parts` | פולט חלקיקים כשהוא דולק |
| `parts` | צג |
| `parts` | שער |
| `PartItems` | בקר |
| `PartItems` | חיישן - קלט |
| `PartItems` | טווח: |
| `PartItems` | מפעיל - פלט |
| `PartItems` | מקור אנרגיה |
| `PartItems` | צריכה: |
| `PartItems` | קיבולת: |
| `PartItems` | רכיב |
| `PartLabels` | \nטוען |
| `PartLabels` | \nכלל |
| `PartLabels` | \nלא מחובר לבקר |
| `PartLabels` | \nלחיצה ימנית |
| `PartLabels` | עצור |
| `PartLabels` | פועל |
| `ComponentBoard` | \nמשימה: |
| `ComponentBoard` | \nנעול |

## Missions and the test bench

| Where | Text |
|---|---|
| `missions` | ADD, זיהוי מעבר, סדר הכללים |
| `missions` | M1 SET TIME ואז M1 ADD 3. הזיכרון מחזיק מתי מותר להיסגר - לא אם מישהו נמצא |
| `missions` | אור דמדומים |
| `missions` | אותה נורת לילה - אבל עם רבע סוללה. אל תיתנו לרובוט להיכבות. |
| `missions` | אזעקה |
| `missions` | אחרי 3 שניות השער נסגר לבד. אם נשאר פתוח - הזיכרון לא נקבע למועד |
| `missions` | אין אף אחד. |
| `missions` | באמצע התחום המחמם ממשיך - הוא כבר דולק ולא הגיע לסף הכיבוי |
| `missions` | באמצע התחום המחמם נשאר כבוי. אם נדלק - יש לכם סף אחד, ולכן ריצוד |
| `missions` | בחושך הנורה צריכה להידלק |
| `missions` | בחושך מוחלט היא כבויה שוב - צריך כלל שלישי שגובר על השני |
| `missions` | ביום הנורה כבויה |
| `missions` | ביום הנורה צריכה להיות כבויה |
| `missions` | ביום כבוי |
| `missions` | ביום כבויה |
| `missions` | בלי אף אחד בסביבה השער סגור |
| `missions` | בלי אף אחד השער סגור |
| `missions` | בלילה דלוק |
| `missions` | בתחום הדמדומים הנורה דולקת |
| `missions` | גם הנורה צריכה להידלק - שני כללים על אותו תנאי |
| `missions` | דלת אוטומטית |
| `missions` | דלת שנשארת פתוחה |
| `missions` | דמדומים. עוצמת אור 7. |
| `missions` | הדליקו את הנורה כשחשוך, וכבו אותה כשאור. |
| `missions` | הדליקו את הנורה רק בין אור לחושך - לא ביום, ולא בחושך מוחלט. |
| `missions` | הוא הלך - עברה שנייה. |
| `missions` | הוא הלך. |
| `missions` | החזיקו את החום בתחום. שימו לב: סף אחד גורם לנורה לרצד. |
| `missions` | הסוללה נגמרה באמצע. מפעיל שדולק כשאין בו צורך שורף את התקציב |
| `missions` | השער נפתח כשמישהו מגיע - ונשאר פתוח עוד 3 שניות אחרי שהלך. |
| `missions` | השער נפתח כשמישהו נמצא בסביבה, ונסגר כשהוא הולך. |
| `missions` | השער צריך להיסגר אחריו |
| `missions` | ובוקר. |
| `missions` | זיכרון ששומר מועד ולא רק דגל; TIME |
| `missions` | חושך מוחלט. עוצמת אור 1. |
| `missions` | חיישן שני, שתי פעולות מתנאי אחד |
| `missions` | חיישן, סף, פלט - ושפלט זוכר את מצבו |
| `missions` | חיסכון |
| `missions` | חם. חום 70. |
| `missions` | יום מלא. עוצמת אור 14. |
| `missions` | יום. |
| `missions` | יום. עוצמת אור 14. |
| `missions` | יצא. |
| `missions` | כדי לספור מעברים צריך לזכור מה היה בסיבוב הקודם: M2 SET S1 - אבל רק אחרי הכלל שמשווה |
| `missions` | כל מפעיל דולק שורף חשמל בכל סיבוב. מה באמת חייב להיות דלוק, ומתי? |
| `missions` | כניסה ראשונה. |
| `missions` | כניסה שנייה. |
| `missions` | כשאין אף אחד קרוב האזעקה שקטה |
| `missions` | כשהאור חוזר הנורה צריכה להיכבות. פלט זוכר את מצבו - צריך כלל שמכבה אותו |
| `missions` | כשהוא מתרחק האזעקה נכבית |
| `missions` | כשחוזרים לתחום היא נדלקת שוב |
| `missions` | כשמזוהה נוכחות הזמזם צריך לפעול |
| `missions` | כשמזוהה נוכחות השער נפתח |
| `missions` | כשמזוהה נוכחות השער צריך להיפתח |
| `missions` | כשמישהו מתקרב - הפעילו זמזם וגם נורה. |
| `missions` | כשקר המחמם צריך לעבוד |
| `missions` | לילה. |
| `missions` | לילה. עוצמת אור 2. |
| `missions` | מאפסים. |
| `missions` | מונה |
| `missions` | מישהו הגיע! |
| `missions` | מישהו הגיע. |
| `missions` | מעל סף הכיבוי המחמם נכבה |
| `missions` | מפעיל מכני |
| `missions` | משוב, היסטרזיס - שני ספים |
| `missions` | מתחמם. חום 45 - באמצע. |
| `missions` | מתקרר. שוב 45 - באמצע. |
| `missions` | נורת לילה |
| `missions` | ספרו כמה פעמים מישהו נכנס. השתמשו בזיכרון M1. |
| `missions` | עברו עוד ארבע שניות. |
| `missions` | עם סף אחד המערכת מתנדנדת. נסו סף להדלקה וסף אחר - גבוה יותר - לכיבוי |
| `missions` | קר. חום 20. |
| `missions` | שוב דמדומים. עוצמת אור 5. |
| `missions` | שוב יום. עוצמת אור 13. |
| `missions` | שלושה כללים על אותו פלט, והסדר הוא התשובה: כבו ביום, הדליקו כשמחשיך, ואז כבו שוב בחושך מוחלט |
| `missions` | שנייה אחרי שהלך השער עדיין פתוח. זה כל העניין - הזיכרון מחזיק מועד |
| `missions` | שתי כניסות - M1 צריך להיות 2. אם יצא מספר גדול, אתם סופרים כל סיבוב במקום כל מעבר |
| `missions` | תחום בין שני ספים; כלל מאוחר גובר - הפעם ככלי, לא כמלכודת |
| `missions` | תקציב אנרגיה, מחזור עבודה |
| `missions` | תרמוסטט |
| `MissionService` | אין |
| `MissionService` | אין כללים בתוכנית. לחצו על הבקר וכתבו כלל. |
| `MissionService` | באותו רגע: |
| `MissionService` | במקום |
| `MissionService` | הבאה בתור: |
| `MissionService` | הצלחה! |
| `MissionService` | הרובוט נעצר: |
| `MissionService` | הרובוט עדיין פועל |
| `MissionService` | הרצת ניסוי |
| `MissionService` | חסר לרובוט: |
| `MissionService` | מחובר לרובוט |
| `MissionService` | נסו שוב: /rc mission |
| `MissionService` | נפתח: |
| `MissionService` | עבר את כל הבדיקות |
| `MissionService` | רמז: |
| `MissionService` | ✖ הבדיקה לא עברה: |

## The rule table

| Where | Text |
|---|---|
| `ProgramMenu` | אז... |
| `ProgramMenu` | איך זה עובד |
| `ProgramMenu` | אנרגיה: |
| `ProgramMenu` | ההשוואה |
| `ProgramMenu` | המפעיל או הזיכרון שמשתנה |
| `ProgramMenu` | המקור שנבדק |
| `ProgramMenu` | הערך להשוואה |
| `ProgramMenu` | הערך לפעולה |
| `ProgramMenu` | הפעולה |
| `ProgramMenu` | הפעלה |
| `ProgramMenu` | הרובוט פועל |
| `ProgramMenu` | כל מפעיל דולק שורף חשמל |
| `ProgramMenu` | כלל |
| `ProgramMenu` | כלל חדש |
| `ProgramMenu` | כלל מאוחר גובר על מוקדם | פלט זוכר את מצבו |
| `ProgramMenu` | כללים |
| `ProgramMenu` | כללים רצים מלמעלה למטה בכל סיבוב |
| `ProgramMenu` | כללים רצים מלמעלה למטה, בכל סיבוב |
| `ProgramMenu` | לחיצה מוסיפה שורה |
| `ProgramMenu` | לחיצה: ±1 | Shift: ±10 | גלגלת: מספר/חיישן |
| `ProgramMenu` | לחיצה: הבא | ימנית: הקודם |
| `ProgramMenu` | לחיצה: הבאה |
| `ProgramMenu` | לחיצה: החליפו למקור אחר |
| `ProgramMenu` | מוחק את הכלל הזה |
| `ProgramMenu` | מחיקה |
| `ProgramMenu` | מפעיל את הלולאה |
| `ProgramMenu` | עוצר את הלולאה |
| `ProgramMenu` | עצירה |
| `ProgramMenu` | פלטים נשארים במצבם - עצירה היא לא איפוס |
| `ProgramMenu` | תוכנית הרובוט |
| `ProgramMenu` | תמיד - בלי תנאי |
| `MenuListener` | אי אפשר להפעיל: |
| `MenuListener` | אין מפעילים מחוברים לבקר. הניחו נורה או שער בקרבת הבקר. |
| `MenuListener` | הגעתם ל- |
| `MenuListener` | הרובוט נעצר. הפלטים נשארו במצבם - עצירה היא לא איפוס. |
| `MenuListener` | הרובוט פועל. |
| `MenuListener` | כללים. זה המקום לחשוב איך לעשות את זה בפחות. |
| `MenuListener` | עצור |

## Building a robot

| Where | Text |
|---|---|
| `PartBlockListener` | - זה השם שמשתמשים בו בתוכנית. |
| `PartBlockListener` | אין בקר בטווח |
| `PartBlockListener` | בלוקים - הרכיב לא מחובר. |
| `PartBlockListener` | בקר הונח. לחיצה ימנית עליו פותחת את התוכנית. |
| `PartBlockListener` | הבקר הוסר. הרכיבים נשארו במקומם אבל אינם מחוברים. |
| `PartBlockListener` | הבקר הזה מלא ( |
| `PartBlockListener` | חובר לבקר בשם |
| `PartBlockListener` | חובר לבקר. |
| `PartBlockListener` | רכיבים שהיו בסביבה התחברו לבקר. |
| `PartBlockListener` | רכיבים). |
| `InteractListener` | אין רובוט שלכם עם סוללה בטווח. |
| `InteractListener` | זה הלוח של מישהו אחר. |
| `InteractListener` | זה הרובוט של מישהו אחר. |
| `InteractListener` | לא מחובר לשום בקר. |
| `InteractListener` | לקחתם: |
| `InteractListener` | נטענו |
| `InteractListener` | עדיין נעול - השלימו את המשימה שפותחת אותו. |
| `InteractListener` | רובוטים. |
| `RobotEngine` | אין כללים בתוכנית |
| `RobotEngine` | אין סוללה מחוברת |
| `RobotEngine` | הסוללה ריקה |
| `RobotEngine` | סוללה ריקה |
| `RobotEngine` | שגיאה |

## Debugging and status

| Where | Text |
|---|---|
| `Trace` | (מתקיים) |
| `Trace` | ==== מצב הרובוט ==== |
| `Trace` | אין כללים. לחצו על הבקר. |
| `Trace` | זיכרון: |
| `Trace` | זמן |
| `Trace` | חיישנים: אין |
| `Trace` | כללים: |
| `Trace` | מפעילים: |
| `Trace` | מפעילים: אין |
| `Trace` | עצור |
| `Trace` | פועל |
| `Trace` | שימו לב: יותר מכלל אחד מתקיים על אותו פלט - הכלל המאוחר גובר. |
| `Trace` | ← זה שקבע |
| `StatusBar` | כל המשימות הושלמו! בנו מנגנון משלכם |
| `StatusBar` | משימה |
| `ProgressReport` | ---- תלמידים ---- |
| `ProgressReport` | ==== התקדמות הכיתה ==== |
| `ProgressReport` | אין בקר |
| `ProgressReport` | משימות: |
| `ProgressReport` | סיים הכול |
| `ProgressReport` | עובדים עכשיו על: |
| `ProgressReport` | עוד לא נכנס אף תלמיד. |
| `ProgressReport` | עצור |
| `ProgressReport` | פועל |
| `ProgressReport` | רכיבים %-3d |
| `ProgressReport` | תלמידים: |

## The assistant

| Where | Text |
|---|---|
| `Whisper` | עוזר/ת סדנה |

## Commands and protection

| Where | Text |
|---|---|
| `RoboCraftCommand` | ---- חימום (לא חובה) ---- |
| `RoboCraftCommand` | . התשובה תגיע לצ'אט. |
| `RoboCraftCommand` | /rc guide | kit | tp | board | missions | mission <id> | run | stop | trace | charge | ask <שאלה> |
| `RoboCraftCommand` | /rc mission <id> - הרשימה: /rc missions |
| `RoboCraftCommand` | ==== משימות ==== |
| `RoboCraftCommand` | config.yml נטען מחדש. תוכן (parts/missions) דורש הפעלה מחדש. |
| `RoboCraftCommand` | אי אפשר להפעיל: |
| `RoboCraftCommand` | אין לכם בקר. הניחו אחד כדי להתחיל. |
| `RoboCraftCommand` | אין לכם הרשאה לפקודה הזו. |
| `RoboCraftCommand` | אין משימה כזו. |
| `RoboCraftCommand` | אין סוללה מחוברת לרובוט. |
| `RoboCraftCommand` | אין רכיב כזה. |
| `RoboCraftCommand` | אין רכיב כזה: |
| `RoboCraftCommand` | דברים שאפשר לבנות גם באבן אדומה. הם כאן כדי להתרגל לכלים. |
| `RoboCraftCommand` | ההתקדמות אופסה. |
| `RoboCraftCommand` | הסוללה מלאה: |
| `RoboCraftCommand` | הרובוט נעצר. |
| `RoboCraftCommand` | הרובוט פועל. |
| `RoboCraftCommand` | השאלה נשלחה ל |
| `RoboCraftCommand` | לא הצלחתי לשמור את השאלה. קראו למורה. |
| `RoboCraftCommand` | לוח הרכיבים נבנה מחדש. |
| `RoboCraftCommand` | מלמד: |
| `RoboCraftCommand` | עוד לא נשאלו שאלות. |
| `RoboCraftCommand` | עוזר/ת סדנה |
| `RoboCraftCommand` | עצור |
| `RoboCraftCommand` | רגע אחד - אפשר לשאול שוב בעוד |
| `RoboCraftCommand` | שאלות אחרונות ==== |
| `RoboCraftCommand` | שימוש: /rc ask <שאלה>   -   למשל: /rc ask למה הנורה לא נדלקת? |
| `RoboCraftCommand` | שניות. |
| `PlotProtection` | זה חלק מהסדנה - אי אפשר לפרק אותו. |
| `PlotProtection` | זו הסדנה של מישהו אחר. /rc tp מחזיר אתכם לשלכם. |

## Other

| Where | Text |
|---|---|
| `config.yml` | עוזר/ת סדנה |

## Added 2026-09-10 - The Yard, step 1. UNPROOFREAD.

Yon proofreads these himself (his decision, 2026-09-10). Nothing below has been read by a
native speaker. `missions.yml` was rewritten in full: sixteen missions, each with a `name`,
`brief`, `teaches`, `hint`, a one-line `value`, a five-line `quest` (48 characters a line,
enforced) and the bench steps. Edit the strings in the YAML itself; this list is for reading.

### New strings in code and parts

| Where | Text |
|---|---|
| `RoboCraftCommand` | ---- בונוס (אחרי הסולם) ---- |
| `RoboCraftCommand` | עבודות לרכיבים שהסולם פתח. אין פרס - רק מה שהרובוט עושה. |
| `RoboCraftCommand` | נפתחות אחרי שהסולם גמור. אפשר לנסות כבר עכשיו, אבל הרכיבים עוד נעולים. |
| `RoboCraftCommand` | הרובוט באמצע הרצת ניסוי - הטעינה תחכה לסיום. |
| `InteractListener` | הרובוט באמצע הרצת ניסוי - הטעינה תחכה לסיום. |
| `MissionService` | אין נורה מחובר לרובוט |
| `MissionService` | נורה (A1) החליף מצב 8 פעמים בזמן ההמתנה - ריצוד |
| `MissionService` | נורה (A1) = דולק במקום כבוי (יש 2 נורה מחוברים - המשימה מצפה לאחד) |
| `MissionService` | M1 = 3 במקום 2 |
| `MissionService` | הסוללה מולאה לרגל ההצלחה. |
| `MissionService` | חימום הושלם |
| `MissionService` | בונוס הושלם |
| `StatusBar` | בונוס 2/7: אזעקת אש |
| `parts` | חיישן חיות |
| `parts` | 1 אם יש חיה או יצור בקרבת מקום - לא אנשים |

### missions.yml, mission by mission


**night_light**

- name: נורת לילה
- brief: הדליקו את הנורה כשחשוך, וכבו אותה כשאור.
- teaches: חיישן, סף, פלט - ושפלט זוכר את מצבו
- value: אור בפתח המכרה כשמחשיך - בלי שאף אחד ייגע בכלום.
- quest: חשוך בפתח המכרה בלילה, והכורים מחפשים את הכניסה.
- quest: בנו על המשטח בקר וסוללה. חיישן אור - על האריח,
- quest: מתחת לשמיים. את הנורה שימו על המדף שבפתח.
- quest: כשמחשיך - שתידלק. כשמאיר - שתיכבה.
- quest: כשהרובוט מוכן, לחצו כאן שוב.
- quest: { say: "יום בפתח המכרה. עוצמת אור 14.", env: { light: 14 }, wait: 30 }
- quest: { expect: { lamp: off }, because: "ביום הנורה צריכה להיות כבויה" }
- quest: { say: "לילה. עוצמת אור 2.", env: { light: 2 }, wait: 30 }
- quest: { expect: { lamp: on }, because: "בחושך הנורה צריכה להידלק" }
- quest: { say: "שוב בוקר. עוצמת אור 13.", env: { light: 13 }, wait: 30 }
- quest: { expect: { lamp: off }, because: "כשהאור חוזר הנורה צריכה להיכבות. פלט זוכר את מצבו - צריך כלל שמכבה אותו" }

**alarm**

- name: שומר המחסן
- brief: כשמישהו מתקרב לדלת המחסן - הפעילו זמזם וגם נורה.
- teaches: חיישן שני, שתי פעולות מתנאי אחד
- value: פעמון דלת ואזעקה למחסן - שומעים אותו מכל החצר.
- quest: במחסן יש כלים לכל החצר, וכולם עוברים בדלת הזו.
- quest: בנו שומר: חיישן נוכחות על האריח ליד הדלת,
- quest: זמזם ונורה על המדף. כשמישהו מגיע - צלצול ואור.
- quest: רמז: כשאתם עומדים ליד הבקר, החיישן רואה גם אתכם.
- quest: כשהרובוט מוכן, לחצו כאן שוב.
- quest: { say: "אין אף אחד ליד המחסן.", env: { player: 0 }, wait: 30 }
- quest: { expect: { buzzer: off }, because: "כשאין אף אחד קרוב האזעקה שקטה" }
- quest: { say: "מישהו הגיע לדלת!", env: { player: 1 }, wait: 30 }
- quest: { expect: { buzzer: on }, because: "כשמזוהה נוכחות הזמזם צריך לפעול" }
- quest: { expect: { lamp: on }, because: "גם הנורה צריכה להידלק - שני כללים על אותו תנאי" }
- quest: { say: "הוא הלך.", env: { player: 0 }, wait: 30 }
- quest: { expect: { buzzer: off }, because: "כשהוא מתרחק האזעקה נכבית" }

**auto_door**

- name: פתח המרתף
- brief: השער בפתח המרתף נפתח כשמישהו נמצא בסביבה, ונסגר כשהוא הולך.
- teaches: מפעיל מכני
- value: דלת ברזל שאין לה מפתח חוץ מהתוכנית שלכם.
- quest: ברצפת המחסן יש פתח למרתף, ובמרתף - ארגז ציוד.
- quest: שער ברזל אי אפשר לפתוח ביד. שימו את השער בחור
- quest: המסומן, וחיישן נוכחות על האריח שלו.
- quest: כשמישהו מגיע - נפתח. כשהוא הולך - נסגר.
- quest: כשהרובוט מוכן, לחצו כאן שוב.
- quest: { say: "אין אף אחד במחסן.", env: { player: 0 }, wait: 30 }
- quest: { expect: { gate: off }, because: "בלי אף אחד בסביבה השער סגור" }
- quest: { say: "מישהו הגיע לפתח.", env: { player: 1 }, wait: 30 }
- quest: { expect: { gate: on }, because: "כשמזוהה נוכחות השער צריך להיפתח" }
- quest: { say: "הוא הלך.", env: { player: 0 }, wait: 30 }
- quest: { expect: { gate: off }, because: "השער צריך להיסגר אחריו" }

**twilight**

- name: מנורת החממה
- brief: הדליקו את המנורה רק בין אור לחושך - לא ביום, ולא בחושך מוחלט.
- teaches: תחום בין שני ספים; כלל מאוחר גובר - הפעם ככלי, לא כמלכודת
- value: החיטה גדלה בדמדומים, והסוללה נשמרת בלילה.
- quest: חיטה גדלה רק כשיש מספיק אור - בערב היא נעצרת.
- quest: חיישן אור בחוץ, מעבר לזכוכית. נורה על הקורה.
- quest: ביום מלא - כבויה, זה בזבוז. כשמחשיך - דולקת.
- quest: בחושך מוחלט - כבויה: הצמחים נחים, וגם הסוללה.
- quest: כשהרובוט מוכן, לחצו כאן שוב.
- hint: שלושה כללים על אותו פלט, והסדר הוא התשובה: כבו ביום, הדליקו כשמחשיך, ואז כבו שוב בחושך מוחלט
- quest: { say: "צהריים בחממה. עוצמת אור 14.", env: { light: 14 }, wait: 30 }
- quest: { expect: { lamp: off }, because: "ביום הנורה כבויה" }
- quest: { say: "דמדומים. עוצמת אור 7.", env: { light: 7 }, wait: 30 }
- quest: { expect: { lamp: on }, because: "בתחום הדמדומים הנורה דולקת" }
- quest: { say: "לילה עמוק. עוצמת אור 1.", env: { light: 1 }, wait: 30 }
- quest: { expect: { lamp: off }, because: "בחושך מוחלט היא כבויה שוב - צריך כלל שלישי שגובר על השני" }
- quest: { say: "לפנות בוקר. עוצמת אור 5.", env: { light: 5 }, wait: 30 }
- quest: { expect: { lamp: on }, because: "כשחוזרים לתחום היא נדלקת שוב" }
- quest: { say: "ובוקר.", env: { light: 12 }, wait: 30 }
- quest: { expect: { lamp: off }, because: "ביום כבויה" }

**timed_door**

- name: פתח שמחכה
- brief: השער נפתח כשמישהו מגיע - ונשאר פתוח עוד 3 שניות אחרי שהלך.
- teaches: זיכרון ששומר מועד ולא רק דגל; TIME
- value: פתח שאפשר באמת לרדת דרכו בסולם בלי שייטרק.
- quest: הפתח מהחימום נטרק ברגע שיורדים בסולם -
- quest: החיישן כבר לא רואה אתכם, והשער נסגר על הראש.
- quest: בנו שער שנשאר פתוח עוד 3 שניות אחרי שהאדם הלך.
- quest: רמז: לזכור מתי מותר להיסגר - לא אם מישהו כאן.
- quest: כשהרובוט מוכן, לחצו כאן שוב.
- hint: M1 SET TIME ואז M1 ADD 3. הזיכרון מחזיק מתי מותר להיסגר - לא אם מישהו נמצא
- quest: { say: "אין אף אחד במחסן.", env: { player: 0 }, wait: 40 }
- quest: { expect: { gate: off }, because: "בלי אף אחד השער סגור" }
- quest: { say: "מישהו הגיע לפתח.", env: { player: 1 }, wait: 30 }
- quest: { expect: { gate: on }, because: "כשמזוהה נוכחות השער נפתח" }
- quest: { say: "הוא ירד בסולם - עברה שנייה.", env: { player: 0 }, wait: 20 }
- quest: { expect: { gate: on }, because: "שנייה אחרי שהלך השער עדיין פתוח. זה כל העניין - הזיכרון מחזיק מועד" }
- quest: { say: "עברו עוד ארבע שניות.", wait: 90 }
- quest: { expect: { gate: off }, because: "אחרי 3 שניות השער נסגר לבד. אם נשאר פתוח - הזיכרון לא נקבע למועד" }

**counter**

- name: מונה בשער
- brief: ספרו כמה פעמים מישהו נכנס בשער החצר, והראו את המספר על הצג.
- teaches: ADD, זיהוי מעבר, סדר הכללים
- value: ספר אורחים בשער - מספר אמיתי שכל מי שנכנס רואה.
- quest: כל מי שנכנס לחצר עובר בשער הזה.
- quest: חיישן נוכחות על האריח ליד השער, צג על העמוד.
- quest: הצג מראה כמה פעמים מישהו נכנס - לא כמה זמן עמד.
- quest: רמז: כדי לספור מעברים, זכרו מה היה בסיבוב הקודם.
- quest: כשהרובוט מוכן, לחצו כאן שוב.
- hint: כדי לספור מעברים צריך לזכור מה היה בסיבוב הקודם: M2 SET S1 - אבל רק אחרי הכלל שמשווה. והצג מראה את M1
- quest: { say: "השער ריק. מאפסים.", env: { player: 0 }, wait: 20 }
- quest: { say: "אורח ראשון נכנס.", env: { player: 1 }, wait: 20 }
- quest: { say: "עבר.", env: { player: 0 }, wait: 20 }
- quest: { say: "אורח שני נכנס.", env: { player: 1 }, wait: 20 }
- quest: { say: "עבר.", env: { player: 0 }, wait: 20 }
- quest: { expect: { M1: 2, display: 2 }, because: "שתי כניסות - M1 צריך להיות 2, והצג צריך כלל SHOW M1 שמראה אותו. אם יצא מספר גדול, אתם סופרים כל סיבוב במקום כל מעבר" }

**flicker**

- name: נורה שלא מרצדת
- brief: העבירו את הנורה לתקרת המנהרה - היא מאירה את החיישן שלה. תקנו את הריצוד.
- teaches: משוב מהעולם: הפלט משנה את הקריאה הבאה. היסטרזיס - שני ספים - על חיישן אור
- value: מנורת מנהרה יציבה גם כשהיא מאירה את החיישן.
- quest: העבירו את הנורה מהפתח אל המדף שבתקרת המנהרה.
- quest: בלילה היא תרצד: נדלקת, מאירה את החיישן,
- quest: החיישן 'רואה יום', הנורה נכבית - וחוזר חלילה.
- quest: תקנו: סף להדלקה כשחשוך, וסף גבוה יותר לכיבוי.
- quest: בין הספים - לא נוגעים. כשמוכן, לחצו כאן שוב.
- hint: שני ספים: הדליקו מתחת ל-7, כבו רק מעל 13 - האור שהנורה עצמה נותנת. בין הספים הפלט זוכר את מצבו
- quest: { say: "יום. עוצמת אור 14.", env: { light: 14 }, wait: 30 }
- quest: { expect: { lamp: off }, because: "ביום כבויה" }
- quest: { say: "לילה. אור 4 - וכשהנורה דולקת, החיישן רואה גם אותה: 13.", env: { light: 4 }, feedback: { light: { actuator: lamp, add: 9, max: 15 } }, wait: 80 }
- quest: { expect: { steady: lamp, lamp: on }, because: "הנורה מרצדת: נדלקת, מאירה את החיישן, 'רואה יום', נכבית, וחוזר חלילה. סף הכיבוי חייב להיות מעל האור שהנורה עצמה נותנת - 13" }
- quest: { say: "בוקר. עוצמת אור 14.", env: { light: 14 }, wait: 30 }
- quest: { expect: { lamp: off }, because: "כשהשמש חוזרת הנורה נכבית. גם עם שני ספים - צריך כלל שמכבה" }
- quest: { say: "ושוב לילה. אור 4.", env: { light: 4 }, wait: 80 }
- quest: { expect: { steady: lamp, lamp: on }, because: "דולקת ויציבה כל הלילה - זה ההבדל בין מכרה שאפשר לעבוד בו לבין סטרובוסקופ" }

**thermostat**

- name: תרמוסטט
- brief: החזיקו את חום הנפחייה בתחום. שימו לב: סף אחד גורם למחמם לרצד.
- teaches: משוב, היסטרזיס - שני ספים, הפעם על חום
- value: מחמם שמחזיק את הנפחייה בתחום - בלי לרצד.
- quest: הכור צריך להישאר חם, אבל לא לוהט.
- quest: חיישן חום על המשטח. הנורה היא המחמם.
- quest: כשקר - דולק. כשחם - כבוי. עם סף אחד הוא מרצד.
- quest: נסו: שימו את החיישן על 'חם' ועל 'קר' וקראו.
- quest: כשהרובוט מוכן, לחצו כאן שוב.
- hint: עם סף אחד המערכת מתנדנדת. נסו סף להדלקה וסף אחר - גבוה יותר - לכיבוי. בדיוק כמו הנורה במכרה
- quest: { say: "קר בנפחייה. חום 20.", env: { heat: 20 }, wait: 30 }
- quest: { expect: { lamp: on }, because: "כשקר המחמם צריך לעבוד" }
- quest: { say: "מתחמם. חום 45 - באמצע.", env: { heat: 45 }, wait: 30 }
- quest: { expect: { lamp: on }, because: "באמצע התחום המחמם ממשיך - הוא כבר דולק ולא הגיע לסף הכיבוי" }
- quest: { say: "חם. חום 70.", env: { heat: 70 }, wait: 30 }
- quest: { expect: { lamp: off }, because: "מעל סף הכיבוי המחמם נכבה" }
- quest: { say: "מתקרר. שוב 45 - באמצע.", env: { heat: 45 }, wait: 30 }
- quest: { expect: { lamp: off }, because: "באמצע התחום המחמם נשאר כבוי. אם נדלק - יש לכם סף אחד, ולכן ריצוד" }

**efficiency**

- name: אור המגדל
- brief: אותה נורת לילה, למרגלות המגדל - אבל עם רבע סוללה. אל תיתנו לרובוט להיכבות.
- teaches: תקציב אנרגיה, מחזור עבודה
- value: אור מגדל שמחזיק לילה שלם על רבע סוללה.
- quest: האור למרגלות המגדל הוא הדרך הביתה בלילה.
- quest: הפעם יש רק רבע סוללה, ואף אחד לא יבוא לטעון.
- quest: חיישן אור על האריח, נורה על המדף.
- quest: כל מפעיל דולק שורף. מה חייב להיות דלוק, ומתי?
- quest: כשהרובוט מוכן, לחצו כאן שוב.
- hint: כל מפעיל דולק שורף חשמל בכל סיבוב. מה באמת חייב להיות דלוק, ומתי?
- quest: { say: "יום למרגלות המגדל.", env: { light: 14 }, wait: 60 }
- quest: { expect: { lamp: off }, because: "ביום כבוי" }
- quest: { say: "לילה.", env: { light: 2 }, wait: 60 }
- quest: { expect: { lamp: on }, because: "בלילה דלוק" }
- quest: { say: "יום.", env: { light: 14 }, wait: 60 }
- quest: { expect: { lamp: off }, because: "ביום כבוי" }
- quest: { expect: { running: true }, because: "הסוללה נגמרה באמצע. מפעיל שדולק כשאין בו צורך שורף את התקציב" }

**fire_alarm**

- name: אזעקת אש
- brief: הצג מראה את החום תמיד; כשעובר את הסף - הזמזם מצלצל. לפיד לא צריך לצלצל.
- teaches: סף שנבחר לפי מספרים אמיתיים (לפיד 52, מאגמה 56); שני פלטים מקריאה אחת
- value: אזעקה שמצלצלת על אש ומאגמה - ולא על לפיד.
- quest: ליד הכור יש מאגמה מתחת לזכוכית. אם משהו נדלק -
- quest: צריך לדעת מיד. חיישן חום על המשטח, צג שמראה
- quest: את החום כל הזמן, וזמזם שמצלצל מעל הסף.
- quest: לפיד אחד מעלה ל-52. בלוק מאגמה אחד - ל-56.
- quest: לפיד זה לא שריפה. כשמוכן, לחצו כאן שוב.
- hint: ALWAYS צג SHOW S1 - ואז סף אחד לזמזם, בין 52 ל-56. וכלל שמכבה כשמתקרר
- quest: { say: "האולם רגוע. חום 50.", env: { heat: 50 }, wait: 30 }
- quest: { expect: { display: 50, buzzer: off }, because: "הצג מראה את החום תמיד; בלי אש - שקט" }
- quest: { say: "מישהו הניח לפיד ליד החיישן. חום 52.", env: { heat: 52 }, wait: 30 }
- quest: { expect: { buzzer: off }, because: "לפיד זה לא שריפה. הסף נמוך מדי - מאגמה מתחילה ב-56" }
- quest: { say: "אש! חום 56.", env: { heat: 56 }, wait: 30 }
- quest: { expect: { buzzer: on, display: 56 }, because: "בלוק מאגמה אחד בטווח = 56. מעל הסף - צלצול" }
- quest: { say: "כיבו. חום 50.", env: { heat: 50 }, wait: 30 }
- quest: { expect: { buzzer: off }, because: "כשמתקרר האזעקה נרגעת - צריך כלל שמכבה" }
- quest: { say: "הכור מתחמם שוב. חום 62.", env: { heat: 62 }, wait: 30 }
- quest: { expect: { buzzer: on }, because: "האזעקה חוזרת - הזמזם מצלצל מחדש בכל התחלה" }

**tunnel_gauge**

- name: מד מנהרה
- brief: הצג מראה כמה המנהרה פנויה; כשמשהו חוסם קרוב - הזמזם מצלצל.
- teaches: חיישן עם כיוון; SHOW של קריאה גולמית; סף על מרחק; 16 פירושו 'לא רואה כלום'
- value: מד שמראה כמה המנהרה פנויה ומצלצל על מפולת.
- quest: כמה המנהרה פנויה? חיישן מרחק על המדף שבפתח,
- quest: מכוון פנימה - עמדו עם הפנים אל המנהרה כשמניחים.
- quest: הוא מודד עד הבלוק הראשון. הצג מראה את המספר.
- quest: משהו חוסם פחות מ-3 בלוקים ממנו - הזמזם מצלצל.
- quest: נסו באמת: שימו אבן במנהרה. כשמוכן, לחצו שוב.
- hint: ALWAYS צג SHOW S1. סף: פחות מ-3 - זמזם ON, אחרת OFF. הקיר בסוף המנהרה נמצא במרחק 11
- quest: { say: "המנהרה פנויה עד הקיר בסוף. מרחק 11.", env: { distance: 11 }, wait: 30 }
- quest: { expect: { display: 11, buzzer: off }, because: "הצג מראה את המרחק שהחיישן קורא, והקיר בסוף לא מפריע" }
- quest: { say: "מפולת! אבן נפלה 2 בלוקים מהחיישן.", env: { distance: 2 }, wait: 30 }
- quest: { expect: { display: 2, buzzer: on }, because: "משהו חוסם קרוב - הזמזם מצלצל" }
- quest: { say: "פינו את האבן. מרחק 11.", env: { distance: 11 }, wait: 30 }
- quest: { expect: { buzzer: off }, because: "כשהדרך פנויה - שקט. צריך כלל שמכבה" }
- quest: { say: "סובבו את החיישן החוצה, אל השמיים. אין שום דבר בטווח.", env: { distance: 16 }, wait: 30 }
- quest: { expect: { display: 16, buzzer: off }, because: "16 זה לא 'רחוק מאוד' - זה 'לא רואה כלום'. הטווח של החיישן נגמר ב-16" }

**pen_guard**

- name: שומר הדיר
- brief: כשכבשה יוצאת מהדיר - אור. והצג סופר כמה פעמים זה קרה.
- teaches: חיישן ליצורים חיים, לא לאנשים; המונה מהשער על חיישן אחר; חמישה כללים בדיוק
- value: אזעקה ומונה לכבשים שברחו - ולא לאנשים שעוברים.
- quest: בדיר שתי כבשים, ומישהו תמיד משאיר את השער פתוח.
- quest: חיישן חיות על האריח מחוץ לגדר - רואה 5 בלוקים,
- quest: לא את הכבשים שבפנים. כבשה בחוץ - נורה.
- quest: וצג שסופר כמה בריחות היו. חיטה בארגז המחסן.
- quest: פתו אותן החוצה ונסו. כשמוכן, לחצו כאן שוב.
- hint: המונה מהשער, עם חיישן חיות במקום נוכחות: השוו לפני שזוכרים, ותנו לצג להראות את M1. בדיוק חמישה כללים
- quest: { say: "שקט בדיר, השער סגור.", env: { mob: 0 }, wait: 20 }
- quest: { expect: { lamp: off, display: 0 }, because: "אין חיה בחוץ - אור כבוי, המונה על 0" }
- quest: { say: "כבשה יצאה!", env: { mob: 1 }, wait: 20 }
- quest: { expect: { lamp: on }, because: "חיה מחוץ לדיר - אור" }
- quest: { say: "החזרתם אותה.", env: { mob: 0 }, wait: 20 }
- quest: { expect: { lamp: off, display: 1 }, because: "בריחה אחת - הצג מראה 1. אם יצא מספר גדול, ספרתם כל סיבוב במקום כל בריחה" }
- quest: { say: "ועוד אחת ברחה.", env: { mob: 1 }, wait: 20 }
- quest: { say: "חזרה.", env: { mob: 0 }, wait: 20 }
- quest: { expect: { display: 2, M1: 2 }, because: "שתי בריחות - 2. זה המונה מהשער, עם חיישן אחר" }

**rain_vent**

- name: צוהר הגשם
- brief: הצוהר בגג פתוח כשיש אור ויבש, וסגור בלילה - וברגע שמתחיל גשם.
- teaches: שני חיישנים; 'וגם' שנכתב ככלל מאוחר שגובר
- value: צוהר בגג החממה שנסגר לבד כשמתחיל גשם.
- quest: בחממה חם ביום. פתחו צוהר בגג כשיש אור -
- quest: וסגרו בלילה, וברגע שמתחיל גשם, שהחיטה לא תיטבע.
- quest: השער בחור שבגג. חיישן גשם על האריח שלו במשטח -
- quest: הוא מרגיש את הגשם של כל העולם, גם מתחת לגג.
- quest: מי גובר - האור או הגשם? כשמוכן, לחצו כאן שוב.
- hint: כלל האור פותח וסוגר; כלל הגשם סוגר - והוא חייב לבוא אחרון, אחרת כלל האור פותח מחדש. בדקו על התוויות איזה חיישן הוא S1
- quest: { say: "יום יבש. אור 14, גשם 0.", env: { light: 14, rain: 0 }, wait: 30 }
- quest: { expect: { gate: on }, because: "ביום יבש הצוהר פתוח - לאוורר את החממה" }
- quest: { say: "התחיל גשם.", env: { rain: 1 }, wait: 30 }
- quest: { expect: { gate: off }, because: "בגשם הצוהר נסגר - גם כשיש אור. כלל הגשם חייב לגבור על כלל האור" }
- quest: { say: "הגשם פסק, אבל ירד לילה. אור 3.", env: { rain: 0, light: 3 }, wait: 30 }
- quest: { expect: { gate: off }, because: "בלילה סגור" }
- quest: { say: "בוקר יבש. אור 14.", env: { light: 14 }, wait: 30 }
- quest: { expect: { gate: on }, because: "כשיבש ויש אור - נפתח שוב" }

**solar_station**

- name: תחנת השמש
- brief: תחנה בראש המגדל שמציגה את האור וחיה מהשמש בלבד. הסוללה כמעט ריקה בהתחלה.
- teaches: מאזן אנרגיה: רווח מול הוצאה. לוח נותן 2 בשמש מלאה; בקר, חיישן וצג שורפים 3
- value: תחנה בראש המגדל שחיה מהשמש ולא צריכה טעינה.
- quest: בראש המגדל תמיד יש שמש - ואף אחד לא יעלה לטעון.
- quest: בנו תחנה שמציגה את עוצמת האור על הצג,
- quest: וחיה מלוחות סולאריים בלבד. כמה לוחות צריך?
- quest: חשבו: כמה נכנס, כמה יוצא. כל חלק מיותר שורף.
- quest: הסוללה מתחילה כמעט ריקה. כשמוכן, לחצו כאן שוב.
- hint: לוח אחד: נכנס 2, יוצא 3 - מפסידים 1 בכל סיבוב. שני לוחות: מרוויחים 1. ובלי חיישנים מיותרים
- quest: { say: "צהריים על הגג. אור 15. הסוללה כמעט ריקה - 40.", env: { light: 15 }, wait: 600 }
- quest: { expect: { running: true, display: 15 }, because: "לוח אחד נותן 2 בסיבוב; בקר, חיישן וצג שורפים 3. עם לוח אחד מפסידים 1 בכל סיבוב - עוד לוח, או פחות חלקים" }
- quest: { say: "לילה. אור 2. אין שמש - חיים על מה שנאגר.", env: { light: 2 }, wait: 200 }
- quest: { expect: { running: true, display: 2 }, because: "בלילה אין טעינה. אם הרובוט נעצר - לא נאגר מספיק ביום" }

**color_lock**

- name: מנעול צבע
- brief: המרתף נפתח רק כשצמר ירוק מונח מתחת לחיישן הצבע.
- teaches: שוויון על קוד; 14 הוא לא 'יותר' מ-11; חיישן שהקלט שלו הוא משהו שמניחים
- value: מנעול למרתף שנפתח רק לצמר בצבע הנכון.
- quest: נעלו את המרתף: השער נפתח רק למפתח הנכון.
- quest: חיישן צבע על האריח 'מפתח' קורא את הבלוק שמתחתיו.
- quest: שברו את האבן שמתחתיו ושימו שם צמר מהארגז במרתף.
- quest: ירוק 13, אדום 14, כחול 11, צהוב 4, כלום 16.
- quest: רק ירוק פותח. כשמוכן, לחצו כאן שוב.
- hint: שני כללים: שווה 13 - פתוח. לא שווה 13 - סגור. קוד לא משווים עם קטן או גדול
- quest: { say: "אין בלוק מתחת לחיישן. צבע 16.", env: { color: 16 }, wait: 30 }
- quest: { expect: { gate: off }, because: "בלי מפתח המרתף סגור" }
- quest: { say: "צמר אדום במפתח. צבע 14.", env: { color: 14 }, wait: 30 }
- quest: { expect: { gate: off }, because: "אדום זה לא המפתח" }
- quest: { say: "צמר כחול. צבע 11.", env: { color: 11 }, wait: 30 }
- quest: { expect: { gate: off }, because: "כחול הוא 11 - קטן מ-13, אבל לא המפתח. קוד לא משווים עם קטן או גדול, רק עם שווה" }
- quest: { say: "צמר ירוק. צבע 13.", env: { color: 13 }, wait: 30 }
- quest: { expect: { gate: on }, because: "ירוק = 13 - המפתח הנכון פותח" }
- quest: { say: "הוציאו את הצמר. צבע 16.", env: { color: 16 }, wait: 30 }
- quest: { expect: { gate: off }, because: "כשהמפתח יוצא המרתף נסגר - צריך כלל שסוגר" }

**manual_override**

- name: מתג ידני
- brief: אור הפתח דולק כשחשוך - וגם בכל פעם שמישהו מרים את המתג האמיתי.
- teaches: 'או' - שתי סיבות לאותו פלט, וסדר כללים שנותן למתג לגבור. הגשר מאבן אדומה לתוכנית
- value: מתג אמיתי שמדליק את אור המכרה מתי שרוצים.
- quest: ליד המשטח יש מתג אמיתי על עמוד אבן. שימו חיישן
- quest: אבן אדומה על האריח שצמוד לעמוד - כשהמתג למעלה
- quest: החיישן קורא 15. הנורה בפתח דולקת כשחשוך,
- quest: וגם בכל פעם שמישהו מרים את המתג - גם ביום.
- quest: שתי סיבות לאותו פלט. כשמוכן, לחצו כאן שוב.
- hint: כללי האור מדליקים ומכבים; כלל המתג מדליק - והוא חייב להיות אחרון, אחרת כלל היום מכבה אותו
- quest: { say: "יום, המתג למטה. אור 14, אות 0.", env: { light: 14, redstone: 0 }, wait: 30 }
- quest: { expect: { lamp: off }, because: "ביום, בלי מתג - כבויה" }
- quest: { say: "מישהו הרים את המתג. אות 15.", env: { redstone: 15 }, wait: 30 }
- quest: { expect: { lamp: on }, because: "המתג גובר על היום - הכלל שלו חייב להיות אחרון" }
- quest: { say: "המתג ירד, וירד לילה. אור 2.", env: { redstone: 0, light: 2 }, wait: 30 }
- quest: { expect: { lamp: on }, because: "בלילה דולקת גם בלי מתג" }
- quest: { say: "בוקר. אור 14.", env: { light: 14 }, wait: 30 }
- quest: { expect: { lamp: off }, because: "ביום בלי מתג - כבויה. שתי סיבות להדליק, ולשתיהן צריך כלל שמכבה" }

## Added 2026-09-11 - the teacher's pause. UNPROOFREAD.

What a student sees when the teacher stops the room, and what the teacher is told back.
The `⏸` and `▶` symbols are deliberate; they draw in Minecraft's font.

| Where | Text |
|---|---|
| `PauseService` | ⏸ הפסקה |
| `PauseService` | עצרו רגע והקשיבו |
| `PauseService` | ▶ ממשיכים |
| `PauseService` | ⏸ הפסקה - חכו למורה |
| `PauseService` | [המורה] <הטקסט של המורה> |
| `config.yml` | המורה |
| `RoboCraftCommand` | הפסקה: <שם> |
| `RoboCraftCommand` | הפסקה לכל הכיתה (12 מחוברים). /rc resume משחרר. |
| `RoboCraftCommand` | שוחרר: <שם> |
| `RoboCraftCommand` | <שם> לא היה בהפסקה. |
| `RoboCraftCommand` | ההפסקה הסתיימה (12 שוחררו). |
| `RoboCraftCommand` | שימוש: /rc say [שם] <טקסט> |
| `RoboCraftCommand` | נשלח ל-12. |
| `RoboCraftCommand` | כולם (accepted in place of `all`) |

## Added 2026-09-13 - the mission card. UNPROOFREAD.

What a student reads on a mission card, in the overview, and on every click that leads to one.
The check rows are composed: `<situation>: <outcome>` from the words below.

| Where | Text |
|---|---|
| `MissionCard` | ▶ 4. נורה שלא מרצדת  /  ✔ 1. מנורת החממה - הושלמה  /  (חימום)  /  (בונוס) |
| `MissionCard` | בונים: ✔ בקר · ✔ סוללה · ○ נורה · ✖ חיישן חום (נעול) |
| `MissionCard` | ✓ <situation>: <outcome> |
| `MissionCard` |   ...ועוד 2 |
| `MissionCard` | [▶ הרצה]  [▶ הרצה שוב]  [▶ נסו שוב]  [רמז]  [כל המשימות]  [פתיחה]  [הכרטיס]  [הכרטיס: מה בונים ומה נבדק] |
| `MissionCard (hover)` | לחצו - מה בונים ומה נבדק  /  הרובוט עולה על הבוחן  /  עזרה קטנה - לא הפתרון  /  הרשימה  /  מה בונים ומה הבוחן בודק  /  פותח את הכרטיס |
| `MissionCard (readings)` | אור 14 / חום 45 / מרחק 11 / צבע 13 / אות 15 / מישהו קרוב / אין אף אחד / גשם / יבש / חיה בחוץ / אין חיה |
| `MissionCard (outcomes)` | נורה דולקת / נורה כבויה / זמזם מצלצל / זמזם שקט / שער פתוח / שער סגור / הצג 2 / M1 = 2 / הרובוט עדיין פועל / הרובוט נעצר / בלי ריצוד |
| `MissionCard (overview)` | ==== משימות ====  /  חימום:  /  הסולם:  /  בונוס: |
| `MissionCard (overview)` | ✔ הושלמה · ● הבאה · ○ עוד לא. לחיצה על שם פותחת את הכרטיס. |
| `MissionCard (overview)` | הבאה בתור: <מספר. שם>  /  כל המשימות הושלמו! בנו מנגנון משלכם. |
| `RoboCraftCommand` | אין משימה כזו: <קלט> |
| `RoboCraftCommand` | אין משימה פתוחה. /rc missions |
| `RoboCraftCommand` | אין רמז ל<שם> - הריצו, והבוחן יגיד מה לא עבד. |
| `RoboCraftCommand` | רמז (<שם>): <הרמז> |
| `Guide` | המשימה הראשונה: <מספר. שם> [פתיחה]  /  המדריך: /rc guide  |  שאלה: /rc ask |
| `ProgramMenu` | המשימה: 4. נורה שלא מרצדת  /  לחיצה: הרובוט עולה על הבוחן  /  ימנית: הכרטיס - מה בונים ומה נבדק |
| `ProgramMenu` | כל המשימות הושלמו  /  בנו מנגנון משלכם |
| `MenuListener` | כל המשימות הושלמו! בנו מנגנון משלכם. |
| `InteractListener` | חיישן חום עדיין נעול - נפתח במשימה <שם> [פתיחה] |
| `MissionRegistry (log)` | (English only - validator warnings) |

### missions.yml - every brief rewritten (behaviour, not place), three hints added, twelve check texts

- **night_light** brief: הנורה דולקת כשחשוך, וכבויה כשיש אור.
- **night_light** hint (new): שני כללים: IF S1 < 7 THEN A1 ON, ואחריו IF S1 >= 7 THEN A1 OFF. נורה זוכרת - חייבים כלל שמכבה
- **alarm** brief: כשמישהו מתקרב - הזמזם מצלצל וגם הנורה נדלקת.
- **alarm** hint (new): ארבעה כללים: על S1 = 1 - זמזם ON ונורה ON; על S1 = 0 - זמזם OFF ונורה OFF
- **auto_door** brief: השער נפתח כשמישהו קרוב, ונסגר כשהוא הולך.
- **auto_door** hint (new): שני כללים: IF S1 = 1 THEN A1 ON, ואחריו IF S1 = 0 THEN A1 OFF
- **twilight** brief: הנורה דולקת רק בדמדומים: לא ביום, לא בחושך מלא.
- **timed_door** brief: נפתח כשמישהו מגיע; נסגר 3 שניות אחרי שהלך.
- **timed_door** check: שנייה אחרי שהלך
- **timed_door** check: 5 שניות אחרי שהלך
- **counter** brief: ספרו כמה פעמים מישהו נכנס - והצג מראה את המספר.
- **counter** check: אחרי שתי כניסות
- **flicker** brief: הנורה מאירה את החיישן שלה - תקנו כדי שלא תרצד.
- **flicker** check: לילה, והנורה מאירה את החיישן
- **flicker** check: ושוב לילה
- **thermostat** brief: המחמם דולק כשקר, כבוי כשחם - ובאמצע לא מרצד.
- **thermostat** check: 45 בדרך למעלה
- **thermostat** check: 45 בדרך למטה
- **efficiency** brief: נורת לילה עם רבע סוללה בלבד - והרובוט לא נכבה.
- **efficiency** check: בסוף הלילה
- **fire_alarm** brief: הצג מראה חום; מעל הסף - זמזם. לפיד לא מצלצל.
- **tunnel_gauge** brief: הצג מראה את המרחק; חוסם קרוב - הזמזם מצלצל.
- **pen_guard** brief: חיה יוצאת - הנורה דולקת. הצג סופר כמה פעמים.
- **pen_guard** check: חזרה, אחרי בריחה אחת
- **pen_guard** check: אחרי שתי בריחות
- **rain_vent** brief: פתוח כשיש אור ויבש; סגור בלילה וברגע שיורד גשם.
- **solar_station** brief: הצג מראה את האור, והרובוט חי מהשמש בלבד.
- **solar_station** check: 30 שניות של שמש
- **solar_station** check: 10 שניות של לילה
- **color_lock** brief: השער נפתח רק כשצמר ירוק (13) מונח מתחת לחיישן.
- **manual_override** brief: דולקת כשחשוך - וגם כשהמתג למעלה, אפילו ביום.
