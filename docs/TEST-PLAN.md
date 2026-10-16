# Test plan - ChemCraft & RoboCraft

Every feature, old and new, as a student or teacher meets it. **The live checklist, with saved results, is the pinned claude.ai page** ("ChemCraft & RoboCraft Test Plan"); this file is the same content as reference text, kept with the code. Item ids are stable - the page's saved results are keyed by them.

Written 2026-10-16. `new` marks what shipped in October 2026 (docs/DESIGN.md 4.12 and the move to the workshop PC) and has not yet been seen on a screen.

139 checks in 12 sections.


## Servers & setup

_The two dev servers plus the per-class ones. Do these first; everything below assumes a running server._

- [ ] **S1** start.bat finds Java and boots  
  Do: Double-click C:\26.2_RoboCraft\start.bat.  
  See: Window prints 'Using Java: C:\26.2_jdk25\bin\java.exe', log ends with 'Done (…s)'. No WARN/ERROR lines besides the offline-mode warning.
- [ ] **S2** Enable lines, validator clean `new`  
  Do: Read logs\latest.log after boot.  
  See: 'RoboCraft enabled: 16 parts, 16 missions.' and 'missions.yml: 16/16 solutions verified on the bench simulation […]'. No 'missions.yml:' or 'parts.yml:' warnings.
- [ ] **S3** stop.bat stops cleanly `new`  
  Do: Double-click stop.bat in the server folder.  
  See: 'Stopping the server', the server window saves and closes, no 'Could not reach it'. Works without any Claude/MCP config on this PC (rcon.ps1 reads the password from server.properties).
- [ ] **S4** ChemCraft starts and stops  
  Do: Same as S1/S3 in C:\26.2_ChemCraft.  
  See: Port 25565, RCON 25576. 'ChemCraft enabled: 25 elements, 11 molecules, 5 reactions, 10 regions.'
- [ ] **S5** Both servers at once  
  Do: Start both. In PowerShell: netstat -an | findstr 2556  
  See: 25565, 25566, 25575, 25576 listening. A client joins each.
- [ ] **S6** Per-class servers boot on their own ports  
  Do: Start C:\26.2_RoboCraft_<Class>\start.bat for each class.  
  See: Liraz 25567, Tuval 25568, Tair 25569, Ophir 25570, Mirit 25571, Shanni_Hilla 25572, Sappir_Ophir 25573. Test (25566/25575) must never run together with the dev server.
- [ ] **S7** A second device joins over the school Wi-Fi  
  Do: From another laptop on 'Tzahar', Minecraft 26.2 → Direct Connect 10.100.14.11 and 10.100.14.11:25566.  
  See: Both join. If not: AP isolation or firewall - check the 'Minecraft Workshop' firewall rules exist.
- [ ] **S8** Client version  
  Do: Try a 26.3 client, then 26.2.  
  See: 26.3 is refused with a version message; 26.2 joins.
- [ ] **S9** Peaceful and the two game rules  
  Do: In the console: difficulty / gamerule pvp / gamerule spawn_monsters  
  See: Peaceful, false, false - on every server. server.properties does not set these on 26.2; set them once per world.
- [ ] **S10** Self-test from the console  
  Do: Type rc selftest in the server window (not over RCON).  
  See: 'selftest: 225/225 - הכול תקין' on the October build. Class servers on the September build: 184/184.
- [ ] **S11** rcon.ps1 from PowerShell  
  Do: C:\26.2_RoboCraft\rcon.ps1 "list"  and  C:\26.2_ChemCraft\rcon.ps1 -Port 25576 "list"  
  See: Player list printed. No .claude.json needed.

## RoboCraft - joining and the plot

- [ ] **RJ1** First join  
  Do: Join with a brand-new name.  
  See: Teleported onto your plot's pad. Title 'RoboCraft / חשים, מחליטים, פועלים'. Four lines: welcome, build hint, 'המשימה הראשונה: W1. נורת לילה [פתיחה]', the guide/ask line. Starter kit: controller, battery, light sensor, lamp.
- [ ] **RJ2** Plot furniture on the pad  
  Do: Look around the arrival spot.  
  See: Component board (16 ghost tiles with labels), trophy shelf beside it (16 dark glass slots), charging pad (waxed chiseled copper). Nothing floating, nothing buried.
- [ ] **RJ3** Returning join `new`  
  Do: Log out and back in.  
  See: 'משימות: 0 / 6 · רכיבים: 7 / 16', 'הבאה בתור: … [פתיחה]', the brief, and a yellow '▶ …' line with the next step of the current mission.
- [ ] **RJ4** Status bar tracks  
  Do: Watch the bossbar as you progress.  
  See: 'חימום 1/3: נורת לילה' (yellow) → after a rung 'משימה n/6' (blue) → after 6/6 'בונוס n/7' (green). A finished warm-up moves the bar.
- [ ] **RJ5** Board tiles hand out parts  
  Do: Right-click an unlocked tile, then a locked one, then a classmate's board.  
  See: 'לקחתם: סוללה' + item. Locked: '<part> עדיין נעול - נפתח במשימה <name> [פתיחה]' (clickable). Classmate's: 'זה הלוח של מישהו אחר.'
- [ ] **RJ6** Pads on real terrain  
  Do: Console: rc survey 16  
  See: Each plot lists a floor height, biome, 'pad flat …'. On a hill the pad is carved level.

## RoboCraft - parts and the robot

- [ ] **RP1** Place a controller  
  Do: Place the lodestone on the pad.  
  See: 'בקר הונח. לחיצה ימנית עליו פותחת את התוכנית.' A floating label above it.
- [ ] **RP2** Parts attach with port names  
  Do: Battery, light sensor, lamp within 4 blocks of the controller.  
  See: 'סוללה חובר לבקר.', 'חיישן אור חובר לבקר בשם S1 …', 'נורה חובר לבקר בשם A1 …'. Labels show the port and the reading.
- [ ] **RP3** Out of range  
  Do: Place a part 5+ blocks away.  
  See: 'אין בקר בטווח 4 בלוקים - הרכיב לא מחובר.' Label says unattached.
- [ ] **RP4** Orphans adopted  
  Do: Place parts first, controller last.  
  See: 'n רכיבים שהיו בסביבה התחברו לבקר.'
- [ ] **RP5** Breaking returns the part  
  Do: Break a lamp; break the controller.  
  See: The PART item drops (not a redstone lamp); its label is gone. Controller: 'הבקר הוסר. הרכיבים נשארו במקומם אבל אינם מחוברים.'
- [ ] **RP6** Part cap  
  Do: Attach a 17th part.  
  See: 'הבקר הזה מלא (16 רכיבים).'
- [ ] **RP7** Right-click a part  
  Do: Empty hand on a sensor; then with a block in hand.  
  See: Empty hand: 'S1  חיישן אור = 14'. Block in hand: it places (building), no message.
- [ ] **RP8** Labels are live  
  Do: Run the robot; shade the light sensor.  
  See: Reading changes about twice a second; the lamp label flips ON/OFF.
- [ ] **RP9** A new battery arrives charged  
  Do: Attach a battery, open the table.  
  See: Energy icon reads 4800.

## RoboCraft - the rule table and the engine

- [ ] **RT1** Open the table  
  Do: Right-click the controller, also with a block in hand. Sneak + right-click to build against it.  
  See: Chest GUI 'תוכנית הרובוט'; chat behind it: 'התוכנית ריקה - לחצו על האמרלד כדי להוסיף כלל.'
- [ ] **RT2** Add a rule  
  Do: Click the emerald.  
  See: A complete starter rule on your own ports, echoed: '1. IF S1 < 7 THEN A1 ON'.
- [ ] **RT3** Edit cells  
  Do: Left/right click source, op, target, verb; number cell ±1, shift ±10; middle-click swaps number↔port.  
  See: Each click echoes the rule in yellow. Hovering the paper shows the whole rule.
- [ ] **RT4** Delete a rule  
  Do: Click the barrier.  
  See: Program re-echoed, renumbered.
- [ ] **RT5** Five-rule cap  
  Do: Try a sixth rule.  
  See: 'הגעתם ל-5 כללים. זה המקום לחשוב איך לעשות את זה בפחות.'
- [ ] **RT6** Run and stop  
  Do: Lime block, then red block.  
  See: 'הרובוט פועל.' / 'הרובוט נעצר. הפלטים נשארו במצבם - עצירה היא לא איפוס.' A lit lamp stays lit after stop.
- [ ] **RT7** The night light, for real  
  Do: IF S1 < 7 THEN A1 ON; IF S1 >= 7 THEN A1 OFF. Run. Cover the sensor, uncover it.  
  See: Lamp on in the dark, off in light. With only the first rule it never turns off (outputs latch).
- [ ] **RT8** Memory and TIME  
  Do: Counter: IF S1 > M2 THEN M1 ADD 1 / ALWAYS THEN M2 SET S1 / ALWAYS THEN A1 SHOW M1 on a presence sensor + display. Walk past twice. /rc trace  
  See: Display shows 2. Trace lists inputs, M1=2, which rule fired last.
- [ ] **RT9** Energy drains  
  Do: Watch the energy icon with the lamp on vs off.  
  See: Faster drain while on (lamp 6/tick). Flat battery: robot stops, icon says 'סוללה ריקה'.
- [ ] **RT10** Charging pad  
  Do: Right-click the pad within 14 blocks; then from far; then with no battery; then mid-bench.  
  See: 'נטענו 1 רובוטים.' / distance message with the reach / 'אין סוללה מחוברת…' / 'הרובוט באמצע הרצת ניסוי - הטעינה תחכה לסיום.'
- [ ] **RT11** Solar charges  
  Do: Attach a panel in daylight, lamp off.  
  See: Energy climbs.
- [ ] **RT12** Lectern button  
  Do: Left-click the lectern in the table; right-click it.  
  See: Left runs the focused mission on the bench; right shows its card.
- [ ] **RT13** Help book  
  Do: Click the book.  
  See: GUI closes; the 8-line guide prints and fits the chat.

## RoboCraft - sensors and actuators

_Each part reads or acts as its label says._

- [ ] **RS1** Light sensor 0-15  
  Do: Day / under a roof / night.  
  See: 15 / low / ~4.
- [ ] **RS2** Distance sensor  
  Do: Place facing a wall 11 blocks away; put a block 2 away; face the sky.  
  See: 11 → 2 → 16 (16 means nothing in range).
- [ ] **RS3** Presence sensor  
  Do: Stand within 5 blocks, walk away.  
  See: 1 → 0.
- [ ] **RS4** Animal sensor  
  Do: A sheep within 5 blocks; a player; an armour stand.  
  See: 1 / 0 / 0.
- [ ] **RS5** Heat sensor  
  Do: A torch near it; a magma block.  
  See: ≈52 / ≈56.
- [ ] **RS6** Colour sensor  
  Do: Wool under it: green, red, blue, yellow, none.  
  See: 13, 14, 11, 4, 16.
- [ ] **RS7** Rain sensor  
  Do: /weather rain then /weather clear  
  See: 1 then 0, even under a roof.
- [ ] **RS8** Redstone sensor  
  Do: A lever on the block next to it, flipped.  
  See: 15 / 0.
- [ ] **RS9** Lamp  
  Do: A1 ON  
  See: Redstone lamp lights without any redstone.
- [ ] **RS10** Buzzer  
  Do: A1 ON  
  See: Note block plays.
- [ ] **RS11** Gate  
  Do: A1 ON / OFF  
  See: Iron trapdoor opens / closes by itself.
- [ ] **RS12** Display  
  Do: ALWAYS THEN A1 SHOW S1  
  See: The display's label shows the number.
- [ ] **RS13** Marker  
  Do: A1 ON  
  See: Sea lantern emits particles while on.
- [ ] **RS14** Copper never weathers  
  Do: Leave a battery and the charging pad for a long session.  
  See: Still waxed copper - still a part.

## RoboCraft - missions and the bench

- [ ] **RM1** Overview  
  Do: /rc missions  
  See: Three one-line tracks (חימום / הסולם / בונוס) with ✔ ● ○, a legend, 'הבאה בתור' and buttons. At most 10 lines.
- [ ] **RM2** The card `new`  
  Do: /rc card W1  (also the [הכרטיס] button)  
  See: Header, goal, 'בונים: ✔ בקר · ○ נורה…', '✓ אור 14: נורה כבויה' rows, buttons [▶ הרצה] [צעד אחר צעד] [רמז] [כל המשימות]. At most 10 lines.
- [ ] **RM3** Run refused with a reason `new`  
  Do: /rc mission W1 with a part missing; with no rules; with a ? left in a rule.  
  See: 'חסר לרובוט: נורה' + card / 'אין כללים בתוכנית…' / 'יש עוד ? בתוכנית - מלאו את המספרים לפני ההרצה.'
- [ ] **RM4** The bench narrates  
  Do: Run W1.  
  See: Action bar: 'יום בפתח המכרה. עוצמת אור 14.' … chat '· …' lines per step.
- [ ] **RM5** Pass `new`  
  Do: Pass W1 for the first time; then run it again.  
  See: Title 'הצלחה!', level-up sound, firework above the board, '✔ נורת לילה עבר את כל הבדיקות - פתרתם לבד', 'הבאה בתור' as a click, class line '★ <name> סיים/ה את נורת לילה'. Second run: no firework, no broadcast.
- [ ] **RM6** Trophy shelf  
  Do: Look at the shelf after a warm-up, a rung, a bonus.  
  See: Iron / gold / emerald in that mission's slot.
- [ ] **RM7** Parts unlock  
  Do: Pass rung 1 (twilight).  
  See: 'נפתח: צג, …', the board tile lights, the part can be taken.
- [ ] **RM8** Fail  
  Do: Run W1 with only the ON rule.  
  See: '✖ הבדיקה לא עברה: נורה (A1) = ON במקום OFF', the because line, 'באותו רגע: S1 = 13, ⚡ …', 'רמז: …', [▶ נסו שוב] [הכרטיס].
- [ ] **RM9** Flicker rung  
  Do: /rc mission 4 with the plain night light; then with OFF only above 13.  
  See: 'נורה (A1) החליף מצב n פעמים בזמן ההמתנה - ריצוד' / pass.
- [ ] **RM10** Timed door  
  Do: /rc mission 2 with M1 SET TIME / M1 ADD 3 / TIME < M1 ON / TIME >= M1 OFF  
  See: Gate still open one second after leaving, closed by five. The reflex door fails the 'שנייה אחרי שהלך' check.
- [ ] **RM11** Counter  
  Do: /rc mission 3  
  See: 'אחרי שתי כניסות' expects M1 = 2 and display 2.
- [ ] **RM12** Thermostat  
  Do: /rc mission 5 with < 30 ON, > 60 OFF; then a single threshold at 45.  
  See: Pass / fails at '45 בדרך למטה'.
- [ ] **RM13** Efficiency  
  Do: /rc mission 6 with ALWAYS THEN A1 ON; then the night light.  
  See: Starts at 150 energy. Always-on: 'הרובוט נעצר: סוללה ריקה'. Night light: pass + 'הסוללה מולאה לרגל ההצלחה.'
- [ ] **RM14** Bonus after the ladder  
  Do: Finish 6/6, then /rc missions  
  See: First bonus suggested only now; labels B1…B7; /rc mission B1 runs it; grants nothing.
- [ ] **RM15** Hint  
  Do: /rc hint, /rc hint 4  
  See: 'רמז (<name>): …' + run button. No mission is without one.
- [ ] **RM16** Every way of naming a mission  
  Do: /rc mission 1, #1, W1, ח1, B1, ב1, night_light  
  See: All resolve; nonsense → 'אין משימה כזו'.

## RoboCraft - the walkthrough and the three paths

_New in October 2026 (DESIGN.md 4.12). Click a mission's name and you get one step at a time; the card is one button away._

- [ ] **RW1** The front door `new`  
  Do: Fresh student: click 'W1. נורת לילה' (or /rc missions W1).  
  See: Header, the brief, 'פרס: השטח הפתוח', '▶ קחו בקר מהלוח ושימו אותו על המשטח.', [הכרטיס] [כל המשימות]. Nothing else.
- [ ] **RW2** Steps advance as parts are placed `new`  
  Do: Place the controller; take a battery from the board; place it; and so on.  
  See: After each placement: '✔ <done>' in grey and '▶ <next>' in yellow. With the part already in your inventory the line says 'שימו סוללה ליד הבקר.' instead of 'קחו … מהלוח'.
- [ ] **RW3** All parts → open the table `new`  
  Do: Place the last needed part.  
  See: '▶ לחיצה ימנית על הבקר פותחת את טבלת הכללים.'
- [ ] **RW4** The choice `new`  
  Do: Right-click the controller.  
  See: Over the open GUI: '▶ איך נכתוב את הכללים? בחרו:' with [אני כותב/ת] [עם עזרה] [תראו לי] [הכרטיס].
- [ ] **RW5** Solo `new`  
  Do: Click [אני כותב/ת]; add a rule.  
  See: 'בהצלחה. הרמז כאן אם צריך: [רמז]' then '▶ כתבו כללים בטבלה. האמרלד מוסיף שורה.' After a rule: '▶ לחצו על הדוכן בטבלה - הרובוט עולה על הבוחן.'
- [ ] **RW6** Guided `new`  
  Do: Click [עם עזרה]; click the ? cells.  
  See: Table shows 'IF S1 < ? THEN A1 ON' and 'IF S1 >= ? THEN A1 OFF' (gold nugget marked ?). Chat: 'הכללים בטבלה. במקום כל ? - מספר שלכם.' and '▶ כלל 1: IF S1 < ? THEN A1 ON - מלאו מספר במקום ה-?'. One click turns ? into 1; the step moves to rule 2; both filled → the run step.
- [ ] **RW7** A ? blocks the bench `new`  
  Do: With a ? still in the table: lectern, /rc mission W1, and the table's run button.  
  See: 'יש עוד ? בתוכנית - מלאו את המספרים לפני ההרצה.' / 'אי אפשר להפעיל: יש עוד ? בתוכנית - מלאו את המספרים'.
- [ ] **RW8** Watch `new`  
  Do: Click [תראו לי].  
  See: 'הדגמה: הרובוט כותב את הפתרון, כלל אחרי כלל.' The table opens; every two seconds a row appears with '1. IF S1 < 7 THEN A1 ON' and 'אם חיישן אור (S1) קטן מ-7 — נורה (A1): ON' in chat; the table closes; 'זו התוכנית. עכשיו הבוחן:'; the bench runs and passes; the pass line ends '- צפיתם בפתרון'.
- [ ] **RW9** Watch on a robot whose ports differ `new`  
  Do: Attach a presence sensor first (S1) and the light sensor second (S2); then [תראו לי] on W1.  
  See: The written rules use S2, and the bench passes.
- [ ] **RW10** Watch refused without the parts `new`  
  Do: Remove the lamp; /rc path watch night_light  
  See: 'חסר לרובוט: נורה'.
- [ ] **RW11** Said once `new`  
  Do: Click through all eight cells of a rule.  
  See: The '▶' step line is printed at most once, not per click.
- [ ] **RW12** A locked part names its mission `new`  
  Do: /rc missions 5 before the heat sensor is unlocked.  
  See: '▶ חיישן חום עדיין נעול - נפתח במשימה: מונה בשער'.
- [ ] **RW13** The second panel `new`  
  Do: /rc missions B5 with one solar panel attached.  
  See: '▶ קחו עוד לוח סולארי מהלוח ושימו ליד הבקר.'
- [ ] **RW14** Done `new`  
  Do: /rc missions W1 after passing it.  
  See: 'הפרס שלכם: השטח הפתוח' and '✔ המשימה הושלמה! [▶ הרצה שוב]'.
- [ ] **RW15** The path is recorded `new`  
  Do: Pass by watching, then pass again solo. Read plugins\RoboCraft\players.yml.  
  See: paths: night_light: watch → solo. Never downgraded.
- [ ] **RW16** Card ↔ walkthrough `new`  
  Do: From the card click [צעד אחר צעד]; from the walkthrough click [הכרטיס]; from the overview click 'הכרטיס: מה בונים ומה נבדק'.  
  See: Each opens the other.

## RoboCraft - perks and the taste

_New in October 2026. A pass pays in Minecraft; the first real check lends it._

- [ ] **RK1** The prize is shown `new`  
  Do: /rc missions W1, W2, 2, 4, 5, 6  
  See: 'פרס: השטח הפתוח' / מהירות / תעופה בחלקה שלכם / כנפי רחיפה / כלב / תעופה בכל מקום.
- [ ] **RK2** Open land gated before W1 `new`  
  Do: Fresh student: /rc wild, /rc tp wild, and the [שטח פתוח] button on a classmate's plot.  
  See: 'השטח הפתוח נפתח אחרי המשימה הראשונה: W1. נורת לילה [פתיחה]'.
- [ ] **RK3** W1 opens the land `new`  
  Do: Pass W1.  
  See: 'נפתח: השטח הפתוח' and 'השטח הפתוח נפתח לכם! [שטח פתוח]'. /rc wild now works.
- [ ] **RK4** An existing spot is kept `new`  
  Do: A student with wild.x/wild.z in players.yml but no W1 (edit a test account, restart).  
  See: /rc wild still works for them.
- [ ] **RK5** Speed (W2) for keeps `new`  
  Do: Pass the alarm; relog; /rc perks  
  See: 'נפתח: מהירות'; speed icon in the HUD, no particles; still there after relog; '/rc perks' → 'הכוחות שלכם: מהירות'.
- [ ] **RK6** Haste (W3), jump (1), night vision (3) `new`  
  Do: Pass each.  
  See: Each effect appears and persists.
- [ ] **RK7** Flight on your own plot (2) `new`  
  Do: Pass the timed door. Double-tap space on your plot; fly over the plot edge; try on open land.  
  See: Flies on the plot. Over the edge: flight cut, slow falling, 'התעופה עובדת רק בחלקה שלכם.' No flight elsewhere.
- [ ] **RK8** Elytra (4) `new`  
  Do: Pass the flicker fix. Drop the elytra and rockets; relog.  
  See: Elytra + 16 rockets given; replaced on relog.
- [ ] **RK9** A wolf (5) `new`  
  Do: Pass the thermostat. Kill the wolf (test); relog.  
  See: A sitting wolf named 'הכלב של <name>' at your feet; a new one after relog.
- [ ] **RK10** Flight anywhere (6) `new`  
  Do: Pass efficiency.  
  See: Flies on open land and classmates' plots too.
- [ ] **RK11** Reset removes them `new`  
  Do: /rc reset (op)  
  See: Effects gone, flight off, '/rc perks' → 'עוד אין'.
- [ ] **RK12** The taste `new`  
  Do: On the alarm (W2): rules that switch the buzzer but never the lamp. Run.  
  See: Buzzer checks pass, the lamp check fails, and after the failure: '✓ הבדיקה הראשונה עברה - טעימה: מהירות ל-10 דקות. הפרס הקבוע מחכה אחרי ההצלחה.' Speed is active; /rc perks shows 'בהשאלה: מהירות (עוד 10 דק')'.
- [ ] **RK13** Once per mission `new`  
  Do: Fail the same way again.  
  See: No second taste line.
- [ ] **RK14** The taste expires `new`  
  Do: Set perks.taste-minutes: 1 in config.yml, /rc reload, earn a taste on another mission.  
  See: After a minute: 'הטעימה נגמרה. הפרס הקבוע מחכה אחרי ההצלחה במשימה.' and the effect is gone. (Then set it back to 10.)
- [ ] **RK15** A pass supersedes the taste `new`  
  Do: Pass the mission while a taste is running.  
  See: /rc perks lists it under 'הכוחות שלכם', no longer 'בהשאלה'.
- [ ] **RK16** No taste where nothing can be lent `new`  
  Do: On W1 (pays only the open land): pass 'night: on' then fail.  
  See: No taste line - a gate cannot be lent.
- [ ] **RK17** The validator catches a bad perk `new`  
  Do: Add perks: [bogus] to a mission, restart, read the log; then revert.  
  See: 'missions.yml: '<id>' lists perk 'bogus' - not one of …'.

## RoboCraft - plots, open land, home

- [ ] **RL1** Own plot  
  Do: Build and dig anywhere on it; try to break the board, shelf, pad.  
  See: Anything goes except the furniture.
- [ ] **RL2** A classmate's plot  
  Do: Dig grass/stone; break their part; break a block they placed; place a block.  
  See: Dig ✓ / refused with 'מישהו אחר בנה את זה…' / refused / 'בחלקה של מישהו אחר אפשר לחפור - אבל לא לבנות.' + [שטח פתוח] [לחלקה שלי].
- [ ] **RL3** Escalation  
  Do: Four blocked attempts within 30 seconds.  
  See: Particles on each; after the fourth, teleported home.
- [ ] **RL4** Open land  
  Do: /rc wild (after W1); build; /rc wild again; /rc wild new; a classmate tries to break your block.  
  See: Same spot remembered; 'new' finds another; 'מישהו אחר בנה את זה. בנו לידו.' for them.
- [ ] **RL5** /rc tp  
  Do: /rc tp, /rc tp home, /rc tp wild, /rc tp bananas  
  See: Plot / home / open land / three buttons for a word that names no place.
- [ ] **RL6** Home  
  Do: /rc sethome on your plot and on open land; on a classmate's plot; die; /rc home; /rc sethome reset  
  See: Set ✓ ✓ / refused / respawn at home / 'הביתה.' / home back on the plot.
- [ ] **RL7** Default respawn  
  Do: No home set: die.  
  See: Respawn on your own plot, not at world spawn.
- [ ] **RL8** World border  
  Do: Walk 1200+ blocks out.  
  See: The border stops you.

## RoboCraft - classroom and assistant

- [ ] **RC1** Pause the room  
  Do: Op: /rc pause  
  See: Every student frozen (no walking, building, clicking, commands); '⏸ הפסקה' pinned; the teacher is not frozen.
- [ ] **RC2** Pause one, with text  
  Do: /rc pause <name> הסתכלו ללוח  
  See: Only that student; text under the title, paged if long; '[המורה] …' echoed in chat.
- [ ] **RC3** Resume  
  Do: /rc resume <name> while the room is paused; then /rc resume  
  See: '▶ ממשיכים'. The named student stays released; the room resumes.
- [ ] **RC4** Big text  
  Do: /rc say מי זוכר?  and a 60-character line  
  See: Short text is the big line; long text pages through the subtitle; nobody frozen.
- [ ] **RC5** From the console  
  Do: rc pause / rc say hi / rc resume over RCON  
  See: All three work; replies in English to the console.
- [ ] **RC6** /rc ask  
  Do: /rc ask למה הנורה לא נדלקת? twice quickly  
  See: 'נשלח', then a cooldown message; questions.jsonl gains a line with rules, readings and the last failed check.
- [ ] **RC7** Questions and whisper  
  Do: Console: rc questions; rc whisper <name> chat היי; title and actionbar variants  
  See: Questions listed; the student receives it privately.
- [ ] **RC8** Joining into a paused room  
  Do: Pause, then a student logs in.  
  See: Frozen on arrival and told.

## RoboCraft - console and admin

- [ ] **RA1** Class view  
  Do: Console: rc progress  
  See: A table with aligned columns, least progress first.
- [ ] **RA2** A card as text  
  Do: Console: rc missions night_light  
  See: The card printed as plain lines.
- [ ] **RA3** Survey and pads  
  Do: rc survey 16 / rc pads 16  
  See: Floors listed / pads carved before anyone joins.
- [ ] **RA4** Admin commands  
  Do: /rc give lamp 2, /rc unlock all, /rc reset, /rc reload, /rc board  
  See: Items given; all tiles lit; progress and perks cleared, plot kept; config reloaded; board, shelf and kiosk rebuilt.
- [ ] **RA5** Non-op refused  
  Do: A student types /rc give lamp  
  See: 'אין לכם הרשאה לפקודה הזו.'
- [ ] **RA6** The wall grows with content  
  Do: Append a part to parts.yml, restart, a student joins.  
  See: Board rebuilt with the new tile; players.yml layout counts updated.
- [ ] **RA7** Tab completion `new`  
  Do: /rc <tab>, /rc card <tab>  
  See: card, path, perks listed; mission ids complete.

## ChemCraft

_Unchanged this term; checked so the move to this PC and the firewall are known good for it too._

- [ ] **C1** First join  
  Do: Join C:\26.2_ChemCraft (localhost) with a new name.  
  See: Plot, welcome title, /cc guide walkthrough, starter kit (logs, raw ores, sand, redstone, bone meal, tools), ghost periodic wall (25 tiles), a reactor station next to it, mission bar 'יסודות: 0 / 25 · הבא: …'.
- [ ] **C2** Build the regions  
  Do: Op, in game: /cc region build  
  See: Ten zones west of the plots (quarry, mines, forest, desert, volcano, river, salt flats, sea, air, gas) with floors, stations and labelled gather nodes.
- [ ] **C3** Gather nodes regenerate  
  Do: Break a raw-iron block in the mines.  
  See: Drops raw iron, a glass placeholder appears, the node returns after ~60 s.
- [ ] **C4** Extract an atom  
  Do: Right-click the smelter (blast furnace) with raw iron.  
  See: Menu → an iron atom item; the Fe tile on your wall lights.
- [ ] **C5** Witnessing and assists  
  Do: A classmate within 8 blocks watches you extract an element they lack; they then do it themselves.  
  See: Their tile turns yellow ('seen'); you get an assist; sidebar 'עוזרים מובילים'; rank prefix at 3/8/15/25 assists.
- [ ] **C6** Atoms as blocks  
  Do: Place an atom; break it.  
  See: Family-coloured glass with a symbol label; the atom item comes back.
- [ ] **C7** A material  
  Do: /chemcraft give C 8; build a 2x2x2 cube.  
  See: The cube becomes a diamond block.
- [ ] **C8** Bonds and a molecule  
  Do: H, O, H in a row; sneak + right-click the faces to set single bonds.  
  See: Bond order cycles; a water sample item is granted; over-bonding warns.
- [ ] **C9** A reaction  
  Do: /chemcraft givemol hydrogen_gas 2, /chemcraft givemol oxygen_gas 1, right-click the reactor (furnace).  
  See: Water + an effect.
- [ ] **C10** Plot shield  
  Do: Build on a classmate's plot four times.  
  See: Refused each time; sent home after the fourth; /cc report counts it.
- [ ] **C11** Visit and travel  
  Do: /cc visit <name> with fewer than 8 assists; /cc tp; /cc region tp mines  
  See: Visit refused until 8 assists; the other two work.
- [ ] **C12** Assistant  
  Do: /cc ask …; then from PowerShell: rcon.ps1 -Port 25576 "cc questions" and "cc whisper <name> chat היי"  
  See: Question recorded; whisper delivered.
- [ ] **C13** Console declines player commands  
  Do: Console: chemcraft give C 8  
  See: 'לשחקנים בלבד.'
- [ ] **C14** Wall and kiosk height  
  Do: Look at the periodic wall.  
  See: Sits on the ground (plot.ground-y -61, wall.offset-y -60), not floating.
