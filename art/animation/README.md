# Движения в прыжке

В `jump_motion.blend` десять действий. Помимо подготовки, приземления, передачи в прыжке и атаки с задней линии, есть `jump_float_serve`, `jump_topspin_serve`, `quick_set`, `quick_attack`, `wing_attack` и `block_jump`. Выбери объект клипа и открой Graph Editor. Дорожки **Game motion offsets** задают смещения к игровой позе. Дорожки **Preview only** двигают демонстрационные модели в сцене.

Из корня проекта экспортируй сохранённую сцену и пересобери игру.

```powershell
& 'C:/Program Files/Blender Foundation/Blender 5.3/blender.exe' --background art/animation/jump_motion.blend --python-exit-code 1 --python art/animation/jump_motion.py
.\gradlew.bat :lwjgl3:packageApp
```

Экспорт сохраняет 65 отсчётов на действие в `core/src/main/resources/animation/jump_motion.csv`. Игра накладывает их на текущую позу по фазе физического прыжка, степени загрузки или времени после приземления. Конечные ключи прыжков и приземления должны оставаться нейтральными, чтобы слой плавно уходил обратно в обычное движение.

Для проверки запусти EXE с `--pose-focus=8` для передач, `--pose-focus=9` для пайпа и вариантов замаха, `--pose-focus=10` для подач или `--pose-focus=15` для удара первым темпом. Клавиша C переключает боковой ракурс. Для связки с реальным касанием открой тренировку «Передача», выбери первый темп клавишей 3 и сыграй ЛКМ под прыжок центрального.

`--pose-focus=11` показывает варианты блока, `--pose-focus=16` — атаку с края и пять вариантов удара с обеих рук, `--pose-focus=17` — ложный разбег и прыжок без касания.

## Рыбка

В сцене `dive_motion.blend` выбери объект **Forward dive**. Его кривые задают положение таза, наклоны корпуса, ведущую и опорную руку и цели для ног. Метки отмечают толчок, вынос руки, контакт ладони с площадкой, скольжение и подъём. Траектория **Preview travel** нужна только для предпросмотра.

```powershell
& 'C:/Program Files/Blender Foundation/Blender 5.3/blender.exe' --background art/animation/dive_motion.blend --python-exit-code 1 --python art/animation/dive_motion.py
.\gradlew.bat :lwjgl3:packageApp
```

Экспорт в `dive_motion.csv` охватывает 1,42 секунды с частотой 100 Гц. Крупный план в галерее поз запускается с `--pose-focus=12`.

## Падение на колено и перекат

Правь ключи объектов **Knee drop** и **Side roll** в одноимённых сценах. Для переката дорожка `bank` задаёт поворот через плечо. В игре клип зеркалится по направлению падения.

```powershell
$blender = 'C:/Program Files/Blender Foundation/Blender 5.3/blender.exe'
& $blender --background art/animation/knee_drop.blend --python-exit-code 1 --python art/animation/dive_motion.py -- --clip=knee_drop
& $blender --background art/animation/side_roll.blend --python-exit-code 1 --python art/animation/dive_motion.py -- --clip=side_roll
```

В галерее поз `--pose-focus=13` чередует перекат в обе стороны, а `--pose-focus=14` показывает падение на колено. Для движения и разбега используй `--pose-focus=1` (шаг, бег, спринт и торможение), `--pose-focus=2` (приставные шаги со сменой направления), `--pose-focus=3` (движение спиной) и `--pose-focus=9` (атакующий прыжок).

## Свет арен и внешность

В `arena_and_players.blend` пять сцен арен и сцена **Player silhouettes**. В сцене арены объект **Shadow reference** задаёт направление тени, его параметр **Angle** задаёт мягкость края, а свойство `shadow_fill` задаёт остаточное освещение от других источников. Расчёт теней в игре выполняется в реальном времени. В сцене силуэтов экспортируются позиции и размеры кубов с признаком `hair_piece`.

```powershell
& 'C:/Program Files/Blender Foundation/Blender 5.3/blender.exe' --background art/animation/arena_and_players.blend --python-exit-code 1 --python art/animation/arena_and_players.py
.\gradlew.bat :lwjgl3:packageApp
```

Для показа приёма запусти EXE с `--pose-focus=7`. Галерея чередует низкий и высокий мяч и сейв одной рукой. `--pose-focus=12` показывает защиту в падении.

## Модели игроков

В сцене `player_models.blend` находятся модели Соколова, Орлова и Смирнова из Северных волков. Каждая деталь привязана к суставу. Объекты с полями `part_asset`, `part_joint` и `part_color` экспортируются как гранёная геометрия. Дополнительные анатомические детали в сцене служат для предпросмотра посадки формы. В игре сочетания лица, корпуса, причёски и экипировки закреплены за составом и именем игрока. Цвет майки, штрихи клуба и номер подставляются из состава матча.

```powershell
& 'C:/Program Files/Blender Foundation/Blender 5.3/blender.exe' --background art/animation/player_models.blend --python-exit-code 1 --python art/animation/player_models.py
.\gradlew.bat :lwjgl3:packageApp
```

`--model-study` показывает тройку с игровой дистанции спереди, со спины и сбоку, затем даёт портрет и профиль каждого игрока. Для повторяемых снимков используй `--venue=CHAMPIONSHIP --capture-at=0.95,2.40,3.85,5.25,6.65,8.05,9.45,10.85,12.25` вместе с `--capture=DIR`. В крупном плане атаки `--pose-focus=9` одинаковые удары чередуются правой и левой рукой.
