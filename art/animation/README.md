# Движения в прыжке

В `jump_motion.blend` четыре действия. Выбери объект `plant`, `jump_set`, `back_attack` или `landing` и открой Graph Editor. Дорожки **Game motion offsets** задают смещения к игровой позе. Дорожки **Preview only** двигают демонстрационные модели в сцене.

Из корня проекта экспортируй сохранённую сцену и пересобери игру.

```powershell
& 'C:/Program Files/Blender Foundation/Blender 5.3/blender.exe' --background art/animation/jump_motion.blend --python-exit-code 1 --python art/animation/jump_motion.py
.\gradlew.bat :lwjgl3:packageApp
```

Экспорт сохраняет 65 отсчётов на действие в `core/src/main/resources/animation/jump_motion.csv`. Игра накладывает их на текущую позу по фазе физического прыжка, степени загрузки или времени после приземления. Конечные ключи прыжков и приземления должны оставаться нейтральными, чтобы слой плавно уходил обратно в обычное движение.

Для проверки запусти EXE с `--pose-focus=8` для передачи или `--pose-focus=9` для атаки.

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
