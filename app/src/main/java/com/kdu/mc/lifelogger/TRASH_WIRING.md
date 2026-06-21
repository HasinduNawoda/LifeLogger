# Trash Screen — Wiring Guide (4 steps, no other files needed)

---

## 1. Add `trashedEntries` state in `LifeLoggerApp()`

```kotlin
var trashedEntries by remember { mutableStateOf(listOf<TrashedEntry>()) }
```

---

## 2. Update `onDeleteClick` in the `HomeScreen(...)` call
Move deleted entries into trash instead of dropping them:

```kotlin
onDeleteClick = { selectedIndexes ->
    val toTrash = entries
        .filterIndexed { i, _ -> i in selectedIndexes }
        .map { TrashedEntry(entry = it) }   // stamps deletedAt = now
    trashedEntries = trashedEntries + toTrash
    entries = entries.filterIndexed { i, _ -> i !in selectedIndexes }
},
```

---

## 3. Add `"trash"` route to the `when(screen)` block

```kotlin
"trash" -> {
    TrashScreen(
        trashedEntries = trashedEntries,
        language       = language,
        onRestoreEntries = { selectedIndexes ->
            val toRestore = trashedEntries
                .filterIndexed { i, _ -> i in selectedIndexes }
                .map { it.entry }
            entries        = entries + toRestore
            trashedEntries = trashedEntries.filterIndexed { i, _ -> i !in selectedIndexes }
        },
        onDeletePermanently = { selectedIndexes ->
            trashedEntries = trashedEntries
                .filterIndexed { i, _ -> i !in selectedIndexes }
        },
        onBackClick = { screen = "home" }
    )
}
```

---

## 4. Wire the Drawer Trash button in `DrawerContent.kt`

The "Trash" `DrawerMenuItem` currently only closes the drawer. Update it:

```kotlin
DrawerMenuItem(
    painter = painterResource(id = R.drawable.bin),
    text = textOf(language, "Trash")
) {
    scope.launch {
        drawerState.close()
        onNavigate("trash")    // <-- add this line
    }
}
```

`onNavigate` is already passed through `HomeScreen` → `DrawerContent`,
so no signature changes are needed anywhere.

---

## 5. Add strings to `textOf()` in `MainActivity.kt`

Copy the full string table from the comment block at the bottom of
`TrashScreen.kt` and paste it into both the `ENGLISH` and `SINHALA`
`when` branches inside `textOf()`.

---

That's everything. No new dependencies, no Gradle changes.
