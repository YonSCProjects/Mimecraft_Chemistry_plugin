# RoboCraft - Hebrew strings, for proofreading

Every piece of player-facing Hebrew in the plugin, grouped by where it appears.

**It was written by a non-native speaker and has never been read by one.** Wording,
gender forms and register all need a pass before this meets a class. ChemCraft's
convention of slashed dual-gender forms (`עוזר/ת`) is not applied consistently here yet.

Latin tokens inside a string - `S1`, `A1`, `ON`, `WHEN`, `/rc trace` - are deliberate and
must stay Latin: a rule row has to read identically in any client.


248 strings.


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
| `Guide` | ==== RoboCraft - איך משחקים ==== |
| `Guide` | WHEN S1 < 7 THEN A1 ON  =  כשהאור קטן מ-7, הדליקו את A1. |
| `Guide` | ·  רכיבים: |
| `Guide` | ברוכים הבאים ל-RoboCraft! זו הסדנה שלכם, והקיר שלפניכם הוא מלאי הרכיבים. |
| `Guide` | הבאה בתור: |
| `Guide` | המשימה הראשונה: /rc missions | המדריך המלא: /rc guide |
| `Guide` | הניחו בקר, סוללה, חיישן ומפעיל קרוב זה לזה - ואז לחצו על הבקר וכתבו כלל. |
| `Guide` | חשים, מחליטים, פועלים |
| `Guide` | כל מנגנון רובוטי הוא לולאה: חיישן נותן מספר, התוכנית מחליטה, המפעיל פועל. |
| `Guide` | משימות: |
| `Guide` | פקודות: /rc guide | /rc kit | /rc tp | /rc missions | /rc trace | /rc charge |
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
| `missions` | אותה נורת לילה - אבל עם רבע סוללה. אל תיתנו לרובוט להיכבות. |
| `missions` | אזעקה |
| `missions` | אין אף אחד. |
| `missions` | באמצע התחום המחמם ממשיך - הוא כבר דולק ולא הגיע לסף הכיבוי |
| `missions` | באמצע התחום המחמם נשאר כבוי. אם נדלק - יש לכם סף אחד, ולכן ריצוד |
| `missions` | בחושך הנורה צריכה להידלק |
| `missions` | ביום הנורה צריכה להיות כבויה |
| `missions` | ביום כבוי |
| `missions` | בלי אף אחד בסביבה השער סגור |
| `missions` | בלילה דלוק |
| `missions` | במרחק קטן הזמזם צריך לפעול |
| `missions` | גם הנורה צריכה להידלק - שני כללים על אותו תנאי |
| `missions` | דלת אוטומטית |
| `missions` | הדליקו את הנורה כשחשוך, וכבו אותה כשאור. |
| `missions` | הוא הלך. |
| `missions` | החזיקו את החום בתחום. שימו לב: סף אחד גורם לנורה לרצד. |
| `missions` | הסוללה נגמרה באמצע. מפעיל שדולק כשאין בו צורך שורף את התקציב |
| `missions` | השער נפתח כשמישהו מתקרב, ונסגר כשהוא הולך. |
| `missions` | השער צריך להיסגר אחריו |
| `missions` | התרחק. מרחק 15. |
| `missions` | חיישן שני, שתי פעולות מתנאי אחד |
| `missions` | חיישן, סף, פלט |
| `missions` | חיסכון |
| `missions` | חם. חום 70. |
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
| `missions` | כשמזוהה נוכחות השער צריך להיפתח |
| `missions` | כשמשהו מתקרב - הפעילו זמזם וגם נורה. |
| `missions` | כשקר המחמם צריך לעבוד |
| `missions` | לילה. |
| `missions` | לילה. עוצמת אור 2. |
| `missions` | מאפסים. |
| `missions` | מונה |
| `missions` | מישהו הגיע. |
| `missions` | מעל סף הכיבוי המחמם נכבה |
| `missions` | מפעיל מכני, נעילת מצב |
| `missions` | משוב, היסטרזיס - שני ספים |
| `missions` | מתחמם. חום 45 - באמצע. |
| `missions` | מתקרב! מרחק 3. |
| `missions` | מתקרר. שוב 45 - באמצע. |
| `missions` | נורת לילה |
| `missions` | ספרו כמה פעמים נפתח השער. השתמשו בזיכרון M1. |
| `missions` | עם סף אחד המערכת מתנדנדת. נסו סף להדלקה וסף אחר - גבוה יותר - לכיבוי |
| `missions` | קר. חום 20. |
| `missions` | רחוק. מרחק 16. |
| `missions` | שוב יום. עוצמת אור 13. |
| `missions` | שתי כניסות - M1 צריך להיות 2. אם יצא מספר גדול, אתם סופרים כל סיבוב במקום כל מעבר |
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

## Commands and protection

| Where | Text |
|---|---|
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
| `RoboCraftCommand` | ההתקדמות אופסה. |
| `RoboCraftCommand` | הסוללה מלאה: |
| `RoboCraftCommand` | הרובוט נעצר. |
| `RoboCraftCommand` | הרובוט פועל. |
| `RoboCraftCommand` | לוח הרכיבים נבנה מחדש. |
| `RoboCraftCommand` | מלמד: |
| `RoboCraftCommand` | עצור |
| `PlotProtection` | זה חלק מהסדנה - אי אפשר לפרק אותו. |
| `PlotProtection` | זו הסדנה של מישהו אחר. /rc tp מחזיר אתכם לשלכם. |
