/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.mascot

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.random.Random

enum class MascotState {
    IDLE,
    PLAYING,
    DANCE,
    GUITAR,
    NOTES,
    LIKE,
    FIRE,
    SEARCH,
    SLEEP,
    DIZZY,
    PARTY,
    COFFEE,
    WAVE,
    LOVE,
    LAUGH,
    THINK,
    JUMP,
    SURPRISED,
    ANNOYED,
    EAT,
    WALK,
}

sealed interface MascotEvent {
    data class TrackStarted(val title: String, val artist: String) : MascotEvent
    data object PlaybackPaused : MascotEvent
    data object PlaybackResumed : MascotEvent
    data object TrackLiked : MascotEvent
    data object TrackUnliked : MascotEvent
    data object TrackSkipped : MascotEvent
    data object TapMascot : MascotEvent
    data object RapidTapMascot : MascotEvent
    data object PetLongPress : MascotEvent
    data object DoubleTapMascot : MascotEvent
    data object LongPressMascot : MascotEvent
    data object DragStart : MascotEvent
    data object DragFast : MascotEvent
    data object WallBounce : MascotEvent
    data object PlatformLanded : MascotEvent
    data object SearchStarted : MascotEvent
    data object IdleLong : MascotEvent
    data object CoffeeTime : MascotEvent

    // UI Navigation events
    data object NavigatedHome : MascotEvent
    data object NavigatedSearch : MascotEvent
    data object NavigatedLibrary : MascotEvent
    data object NavigatedSettings : MascotEvent
    data object NavigatedHistory : MascotEvent

    // Library section events
    data object LibraryGeneral : MascotEvent
    data object LibrarySongs : MascotEvent
    data object LibraryArtists : MascotEvent
    data object LibraryAlbums : MascotEvent
    data object LibraryPlaylists : MascotEvent
    data object LibraryPodcasts : MascotEvent
    data object LibrarySpotify : MascotEvent
    data object LibrarySoundCloud : MascotEvent

    // Wandering & UI reaction events
    data object WanderWalk : MascotEvent
    data class VolumeChanged(val isUp: Boolean) : MascotEvent
    data object PlayerMenuOpened : MascotEvent
}

object MascotEventBus {
    private val _events = MutableSharedFlow<MascotEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<MascotEvent> = _events.asSharedFlow()

    fun emit(event: MascotEvent) {
        _events.tryEmit(event)
    }
}

interface MascotCharacter {
    val id: String
    val displayName: String
    val description: String
    fun getAnimationAsset(state: MascotState): String
    fun getRandomLine(event: MascotEvent): String
}

object ClaudieMascot : MascotCharacter {
    override val id: String = "claudie"
    override val displayName: String = "Клодик"
    override val description: String = "Любознательный антропоморфный компаньон в оранжевом свитере"

    private val idleAssets = listOf(
        "mascots/claudie/idle.json",
        "mascots/claudie/001_AgADsqcAAob5WUo.json",
        "mascots/claudie/002_AgADx6MAAs_5UUo.json",
        "mascots/claudie/010_AgADqLAAAi1cUEo.json",
        "mascots/claudie/016_AgADOKoAApbMUUo.json",
        "mascots/claudie/021_AgADsqIAAqbJUEo.json",
    )

    private val waveAssets = listOf(
        "mascots/claudie/006_AgADGLQAAhThUEo.json",
        "mascots/claudie/013_AgADD6EAAoHRWUo.json",
        "mascots/claudie/024_AgADWKkAAjB4YEo.json",
        "mascots/claudie/040_AgAD1agAAp7zYEo.json",
    )

    private val loveAssets = listOf(
        "mascots/claudie/like.json",
        "mascots/claudie/003_AgADFbEAAkhSWUo.json",
        "mascots/claudie/011_AgADKq8AAmHuUUo.json",
        "mascots/claudie/027_AgAD-6IAAuc-YUo.json",
        "mascots/claudie/043_AgADA6oAAqJLYUo.json",
    )

    private val laughAssets = listOf(
        "mascots/claudie/005_AgADk6YAAn1LWEo.json",
        "mascots/claudie/018_AgAD6KMAAoTcUUo.json",
        "mascots/claudie/031_AgAD5KYAAlXvYUo.json",
        "mascots/claudie/047_AgADrKEAAtB00Uo.json",
    )

    private val annoyedAssets = listOf(
        "mascots/claudie/008_AgADvaYAAnSAUEo.json",
        "mascots/claudie/020_AgADc6oAAnNpUUo.json",
        "mascots/claudie/033_AgADtK4AAiuiWEo.json",
        "mascots/claudie/049_AgADI6gAAk6k0Uo.json",
    )

    private val eatAssets = listOf(
        "mascots/claudie/007_AgADFqkAAg16UEo.json",
        "mascots/claudie/028_AgADFKsAAgYXYUo.json",
        "mascots/claudie/044_AgAD3qoAAt-k0Uo.json",
    )

    private val coffeeAssets = listOf(
        "mascots/claudie/coffee.json",
        "mascots/claudie/007_AgADFqkAAg16UEo.json",
        "mascots/claudie/028_AgADFKsAAgYXYUo.json",
    )

    private val jumpAssets = listOf(
        "mascots/claudie/014_AgAD4a0AAqsaUEo.json",
        "mascots/claudie/025_AgADhJ8AAsaYYUo.json",
        "mascots/claudie/038_AgADIa8AAjEFYEo.json",
    )

    private val thinkAssets = listOf(
        "mascots/claudie/004_AgADxa0AAqlbWUo.json",
        "mascots/claudie/017_AgADeKwAAj5DWUo.json",
        "mascots/claudie/032_AgADJagAAh6VYEo.json",
    )

    private val danceAssets = listOf(
        "mascots/claudie/dance.json",
        "mascots/claudie/041_AgADtKMAAgkZWEo.json", // Crab Rave
        "mascots/claudie/074_AgADaawAAlp50Eo.json", // BreakDancing
        "mascots/claudie/004_AgADxa0AAqlbWUo.json", // Clawd-Dancing-native
        "mascots/claudie/015_AgADpqcAAqBVWUo.json",
    )

    private val guitarAssets = listOf(
        "mascots/claudie/guitar.json",
        "mascots/claudie/005_AgADk6YAAn1LWEo.json", // Clawd-Guitar-native
    )

    private val notesAssets = listOf(
        "mascots/claudie/notes.json",
        "mascots/claudie/058_AgADoKAAAkG20Eo.json", // Clawd-Swaying
    )

    private val fireAssets = listOf(
        "mascots/claudie/fire.json",
        "mascots/claudie/030_AgADVKwAAtUnWEo.json",
    )

    private val searchAssets = listOf(
        "mascots/claudie/search.json",
        "mascots/claudie/029_AgADqq0AAuOfWUo.json",
    )

    private val sleepAssets = listOf(
        "mascots/claudie/sleep.json",
        "mascots/claudie/012_AgAD3rMAAp-EUEo.json",
    )

    private val dizzyAssets = listOf(
        "mascots/claudie/dizzy.json",
        "mascots/claudie/019_AgADJ6IAAjpuWEo.json",
        "mascots/claudie/034_AgADtq8AAl_kWUo.json",
    )

    private val partyAssets = listOf(
        "mascots/claudie/party.json",
        "mascots/claudie/022_AgADKacAApX1WEo.json",
        "mascots/claudie/037_AgAD5aAAApFRWEo.json",
    )

    private val playingAssets = listOf(
        "mascots/claudie/playing.json",
        "mascots/claudie/068_AgADqakAAqIQ0Eo.json", // Clawd-Headphones
    )

    private val walkAssets = listOf(
        "mascots/claudie/020_AgADc6oAAnNpUUo.json", // Clawd-Walking
        "mascots/claudie/003_AgADFbEAAkhSWUo.json", // Clawd-CrabWalking
    )

    private val surprisedAssets = listOf(
        "mascots/claudie/009_AgADAqsAAkZQWEo.json",
        "mascots/claudie/021_AgADsqIAAqbJUEo.json",
    )

    override fun getAnimationAsset(state: MascotState): String =
        when (state) {
            MascotState.IDLE -> idleAssets.random()
            MascotState.PLAYING -> playingAssets.random()
            MascotState.DANCE -> danceAssets.random()
            MascotState.GUITAR -> guitarAssets.random()
            MascotState.NOTES -> notesAssets.random()
            MascotState.LIKE -> loveAssets.random()
            MascotState.FIRE -> fireAssets.random()
            MascotState.SEARCH -> searchAssets.random()
            MascotState.SLEEP -> sleepAssets.random()
            MascotState.DIZZY -> dizzyAssets.random()
            MascotState.PARTY -> partyAssets.random()
            MascotState.COFFEE -> coffeeAssets.random()
            MascotState.WAVE -> waveAssets.random()
            MascotState.LOVE -> loveAssets.random()
            MascotState.LAUGH -> laughAssets.random()
            MascotState.THINK -> thinkAssets.random()
            MascotState.JUMP -> jumpAssets.random()
            MascotState.SURPRISED -> surprisedAssets.random()
            MascotState.ANNOYED -> annoyedAssets.random()
            MascotState.EAT -> eatAssets.random()
            MascotState.WALK -> walkAssets.random()
        }

    // Rich phrases inspired by clawd_pet.plugin & Telegram Pet engine
    private val trackStartedLines = listOf(
        "О, этот трек качает! 🎶",
        "Отличный выбор, делаем громче! 🔊",
        "Бит мощный, я уже танцую! 🕺",
        "Звучит сочно, чистый кайф! ✨",
        "Заряжаемся звуком на полную! 🔥",
        "Мои сенсоры уловили шедевр! 🎧",
        "Этот дроп просто разнос! 💥",
        "Басы прямо в душу! 🚀",
        "Вот это я понимаю — саундтрек дня! ⚡",
        "О, музыка! Танцуем! ♪♪♪",
        "Это мой любимый трек! 🎵",
        "Врубай на максимум, соседи оценят! 🔈",
        "Ууу, какой вайб пошёл! 🌊",
        "Мои ушки в экстазе! 🐾",
        "С этим треком всё пишется в два счёта! 💻",
        "Наваливаем звука! 🚀",
        "Басы качают пол! 🎸",
    )

    private val resumedLines = listOf(
        "Музыка снова с нами! Погнали! 🚀",
        "Продолжаем разнос колонок! 🔊",
        "Вайб вернулся в чат! ✨",
        "Танцуем дальше! 🕺",
        "Погнали! Басы на старт! ⚡",
        "Пауза отменяется, кайфуем! 🎧",
        "С возвращением в ритм! 🎶",
        "Только не останавливай! 🔥",
    )

    private val pausedLines = listOf(
        "Пауза? Я пока кофе попью ☕",
        "Отдыхаем? Жду продолжения!",
        "Тишина в эфире... но ненадолго!",
        "Замерли в ожидании следующего трека 👀",
        "Ставлю ушки на паузу... ⏸️",
        "Эх, закончилось... Ещё треков! 🎶",
        "Хороший был трек, давай следующий!",
        "Снял наушники, жду отмашки 🎧",
        "Режим тишины активирован... 🤫",
        "Ну вот, на самом интересном месте! 🐾",
    )

    private val likedLines = listOf(
        "Добавлено в сердечко! ❤️",
        "Твой музыкальный вкус — топ! 🌟",
        "Залайкано на века! Будет играть на репите 🔁",
        "Этот трек достоин золотого фонда! ✨",
        "В избранное! Я бы и сам лайкнул дважды! 💖",
        "Любовь к хорошей музыке! ♥♥♥",
        "Чистый шедевр, однозначно в любимые! 💎",
        "Плюс один алмаз в коллекцию! 🏆",
    )

    private val unlikedLines = listOf(
        "Убрали из лайков? Ну ладно, вкусы меняются 💔",
        "Ушло из сердечка, но место для нового свободно! 🍃",
        "Понял, вычеркиваем из любимых ✍️",
        "Не выдержал проверки временем? Бывает! 🍂",
    )

    private val skippedLines = listOf(
        "Не зашло? Давай следующий! ⏩",
        "Скипаем без сожалений!",
        "Ищем алмазы в плейлисте дальше 🔎",
        "Следующий точно разорвёт! 🚀",
        "Переключаем на скорости звука! 💨",
        "Мимо! Следующий кандидат, на сцену! 🎙️",
    )

    private val tapLines = listOf(
        "Тык! Я тут, на связи! 🦀",
        "Чего изволите, меломан? 👋",
        "Ой! Хи-хи! 😊",
        "Я тут! Пип-пип!",
        "Могу сбацать соло на гитаре, если хочешь! 🎸",
        "Качество на максимуме, вайб на пределе! ⚡",
        "Я Клодик, твой верный спутник!",
        "Под этот звук код пишется сам собой! 💻",
        "Не забудь попить водички или чайку 🫖",
        "Всё под контролем, музыка играет как надо! 🎶",
        "Хм... О чём задумался? 🤔",
        "Чего стоим, кого ждём? Давай музло! 🎧",
        "Лапки в тепле, свитер греет 🧡",
        "Клодик онлайн и готов к движу! 🚀",
    )

    private val annoyedLines = listOf(
        "Хватит тыкать! Щекотно же! 😾",
        "Эй, полегче на поворотах! ⚡",
        "Ну всё, обиделся! Не тыкай так часто! 😤",
        "Мои сенсоры перегружены твоими кликами! 🛑",
        "Дай спокойно послушать трек! 🐾",
        "Экран протрёшь до дыр, прекращай! 🛑",
        "Я тебе не кнопка! 😾",
    )

    private val petLines = listOf(
        "Мрр… Как приятно ♥",
        "Ещё погладь! Уютно... 🥰",
        "Лучший день! Мягкий свитер греет 🧡",
        "Хи-хи, щекотно и лампово ✨",
        "Обнимашки? Я только за! 🤗",
        "Ты лучший хозяин на свете! 💖",
        "Так тепло и спокойно... Замурчу сейчас! 🐾",
        "Самый добрый жест за весь день 🥰",
    )

    private val doubleTapLines = listOf(
        "Опа, двойной клик! Лови переключение! ⏯️",
        "Быстрый контроль в деле! ⚡",
        "Понял, переключаю режим воспроизведения! 🎵",
        "Магия двух тыков сработала! ✨",
        "Хоба! Сменил трек в один миг! 💫",
    )

    private val dragLines = listOf(
        "Ааа! Куда мы летим? 🚀",
        "Уиии, невесомость! 🛸",
        "Осторожнее, держи крепче! 💫",
        "Полетели! Физика на высоте! 🌪️",
        "Я не птица, но лечу с кайфом!",
        "Поставь меня на полочку! 🐾",
        "Смена дислокации! Держись за поручни! 🎢",
    )

    private val wallBounceLines = listOf(
        "Бум! Мягкая посадка 🦘",
        "Опа, прилетели к стеночке! 🧱",
        "Вот это ускорение! Физика на 10 из 10 🌪️",
        "Тормоза придумали трусы! 🚀",
        "Пружиним от края экрана! 💨",
        "Ой, бортик! Но я в порядке, свитер самортизировал! 🛡️",
    )

    private val platformLandedLines = listOf(
        "Хоп! Приземлился прямо на платформу! 🐾",
        "Уверенно стою на элементах интерфейса! 📐",
        "Идеальная посадка на панель! 🎯",
        "Тут отличный обзор на кнопки! ✨",
        "Платформа проверена, стоять удобно! 🐾",
        "Ножки ровно на панельке, баланс идеален! 🧘",
    )

    private val idleLines = listOf(
        "Zzz... разбуди, когда нажмешь Play 💤",
        "Я чуть вздремнул на динамике... 🥱",
        "Тихо... идеальное время для чашки кофе ☕",
        "Можно я тут просто тихо посижу? 👀",
        "Пишу алгоритм идеального звучания... 🧠",
        "Мечтаю о новых саундтреках... ☁️",
        "Тишина это тоже музыка, но с битами лучше! 🎧",
    )

    private val searchLines = listOf(
        "Ищу самые свежие бэнгеры! 🔍",
        "Сканирую вселенную звука... 🌐",
        "Сейчас найдем что-нибудь эпичное! 🕵️",
        "Вбивай название, найдём даже самый редкий ремикс! 🎧",
    )

    private val navHomeLines = listOf(
        "Главная сцена! Тут всё самое свежее 🏠",
        "Что послушаем из быстрых подборок? ✨",
        "Лента рекомендаций готова к запуску! 🚀",
    )

    private val navLibraryLines = listOf(
        "О, библиотека! Сколько тут сокровищ 📚",
        "Твой личный музыкальный архив! Что включим? 🎧",
        "Здесь собрана вся твоя коллекция ✨",
        "Листаем архив? Глаза разбегаются! 👀",
    )

    private val navSearchLines = listOf(
        "Поисковый радар включен! Что ищем? 🔎",
        "Любой трек, артист или плейлист найдётся тут! 🌐",
    )

    private val navSettingsLines = listOf(
        "Настройки! Подкрутим басы или настроим тему? ⚙️",
        "Лаборатория звука и внешнего вида открыта! 🛠️",
    )

    private val navHistoryLines = listOf(
        "Ностальгия тайм? Вспоминаем, что играло! 🕰️",
        "Хроника твоих музыкальных приключений 📜",
        "История помнит каждый твой любимый дроп! 💾",
    )

    private val libSongsLines = listOf(
        "Все треки в одном месте! Отборная годнота 🔥",
        "Сколько же тут часов кайфа накоплено! 🎶",
        "Жми на рандом или выберем проверенный хит? 🎲",
        "Коллекция песен — просто золото! 💎",
    )

    private val libArtistsLines = listOf(
        "Твой персональный зал славы артистов! 🎤",
        "Ого, кого тут только нет! Вкусы у тебя разносторонние 🌟",
        "Посмотрим, кто сегодня в топе твоих колонок? 🎸",
        "Легенды музыки на одной полке! 👑",
    )

    private val libAlbumsLines = listOf(
        "Альбомы целиком — это выбор истинных ценителей! 💿",
        "Концептуальные релизы от корки до корки 📖",
        "Какой винил поставим крутиться сегодня? 🎶",
    )

    private val libPlaylistsLines = listOf(
        "Плейлисты под любое настроение и вайб! 📁",
        "Для качалки, для кода или для ночных поездок? 🚗",
        "Идеальный порядок в плейлистах — моё почтение! 👌",
        "Твои авторские подборки — шедевры! ✨",
    )

    private val libPodcastsLines = listOf(
        "Подкасты? Время прокачать мозги или послушать истории 🎙️",
        "Включай, я тоже послушаю с умным видом 🤓",
    )

    private val libSpotifyLines = listOf(
        "Синхронизировано со Spotify! Всё под рукой 🟢",
        "Твои спотифай-плейлисты на максималках! ⚡",
        "Зеленый гигант на связи через наш плеер! 🎧",
    )

    private val libSoundCloudLines = listOf(
        "SoundCloud! Время андеграунда, ремиксов и эксклюзивов 🟠",
        "Свежий фонк, хардстайл и ноунейм бэнгеры прямо здесь! 🔊",
        "Ищем редкие дропы из глубин саундклауда! 🔍",
    )

    private val wanderLines = listOf(
        "Гуляю по экрану... Всё чисто! 🐾",
        "Осматриваю интерфейс — дизайн огонь! ✨",
        "У вас тут так уютно, пойду гляну динамики 🎧",
        "Пробежка под отличный бит! 🏃",
        "Ищу лучший ракурс, чтобы слушать музыку 👀",
        "Патрулирую плеер. Нарушений хорошего вкуса не обнаружено! 🛡️",
        "Разминаю лапки, пока играет любимый трек! 🐾",
        "Шаг влево, шаг вправо — в такт басам! 🎶",
        "Прогуливаюсь по мини-плееру, как по набережной! 🌊",
    )

    private val volumeUpLines = listOf(
        "О да, делаем погромче! 🔊",
        "Качает! Пусть все вокруг слышат! 💥",
        "Добавили мощности звука! 🚀",
        "Звук на максимум, кайф на пределе! ⚡",
        "Вот теперь качает как надо! 🔥",
    )

    private val volumeDownLines = listOf(
        "Потише? Понял, переходим в ночной режим 🤫",
        "Чуткий слух — залог долгой любви к музыке 🎧",
        "Ламповый шепот колонок тоже звучит круто ✨",
        "Убавили децибелы, бережём ушки 🐾",
    )

    private val playerMenuLines = listOf(
        "О, меню трека! Ищем другие версии или ремиксы? 📂",
        "Тут спрятаны суперсилы плеера! ⚡",
        "Хороший трек заслуживает детального изучения! 🔍",
    )

    override fun getRandomLine(event: MascotEvent): String =
        when (event) {
            is MascotEvent.TrackStarted -> {
                if (event.title.isNotBlank()) {
                    if (Random.nextBoolean()) {
                        "Слушаем: ${event.title} 🎵"
                    } else {
                        trackStartedLines.random()
                    }
                } else {
                    trackStartedLines.random()
                }
            }
            is MascotEvent.PlaybackPaused -> pausedLines.random()
            is MascotEvent.PlaybackResumed -> resumedLines.random()
            is MascotEvent.TrackLiked -> likedLines.random()
            is MascotEvent.TrackUnliked -> unlikedLines.random()
            is MascotEvent.TrackSkipped -> skippedLines.random()
            is MascotEvent.TapMascot -> tapLines.random()
            is MascotEvent.RapidTapMascot -> annoyedLines.random()
            is MascotEvent.PetLongPress -> petLines.random()
            is MascotEvent.DoubleTapMascot -> doubleTapLines.random()
            is MascotEvent.LongPressMascot -> petLines.random()
            is MascotEvent.DragStart, is MascotEvent.DragFast -> dragLines.random()
            is MascotEvent.WallBounce -> wallBounceLines.random()
            is MascotEvent.PlatformLanded -> platformLandedLines.random()
            is MascotEvent.IdleLong -> idleLines.random()
            is MascotEvent.CoffeeTime -> pausedLines.random()
            is MascotEvent.SearchStarted -> searchLines.random()
            is MascotEvent.NavigatedHome -> navHomeLines.random()
            is MascotEvent.NavigatedLibrary -> navLibraryLines.random()
            is MascotEvent.NavigatedSearch -> navSearchLines.random()
            is MascotEvent.NavigatedSettings -> navSettingsLines.random()
            is MascotEvent.NavigatedHistory -> navHistoryLines.random()
            is MascotEvent.LibraryGeneral -> navLibraryLines.random()
            is MascotEvent.LibrarySongs -> libSongsLines.random()
            is MascotEvent.LibraryArtists -> libArtistsLines.random()
            is MascotEvent.LibraryAlbums -> libAlbumsLines.random()
            is MascotEvent.LibraryPlaylists -> libPlaylistsLines.random()
            is MascotEvent.LibraryPodcasts -> libPodcastsLines.random()
            is MascotEvent.LibrarySpotify -> libSpotifyLines.random()
            is MascotEvent.LibrarySoundCloud -> libSoundCloudLines.random()
            is MascotEvent.WanderWalk -> wanderLines.random()
            is MascotEvent.VolumeChanged -> if (event.isUp) volumeUpLines.random() else volumeDownLines.random()
            is MascotEvent.PlayerMenuOpened -> playerMenuLines.random()
        }
}

object MascotRegistry {
    val characters: List<MascotCharacter> = listOf(ClaudieMascot)

    fun getCharacter(id: String): MascotCharacter =
        characters.firstOrNull { it.id == id } ?: ClaudieMascot
}
