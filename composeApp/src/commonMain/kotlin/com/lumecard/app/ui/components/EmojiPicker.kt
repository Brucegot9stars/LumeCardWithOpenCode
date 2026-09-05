package com.lumecard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumecard.app.i18n.I18nManager
import org.koin.compose.koinInject

internal data class EmojiEntry(val emoji: String, val tags: List<String> = emptyList())

internal data class EmojiCategory(val key: String, val sample: String, val entries: List<EmojiEntry>)

/** Built-in emoji icon library (a compact categorized collection) with name-based search. */
internal object EmojiLibrary {
    val categories: List<EmojiCategory> = listOf(
        EmojiCategory("smileys", "😀", listOf(
            EmojiEntry("😀", listOf("grin", "smile", "face")),
            EmojiEntry("😃", listOf("smile", "happy", "mouth", "face")),
            EmojiEntry("😄", listOf("smile", "happy", "grin", "face")),
            EmojiEntry("😁", listOf("grin", "teeth", "happy", "face")),
            EmojiEntry("😆", listOf("laugh", "squint", "happy", "face")),
            EmojiEntry("😅", listOf("sweat", "smile", "happy")),
            EmojiEntry("😂", listOf("joy", "laugh", "tears")),
            EmojiEntry("🤣", listOf("rofl", "laugh", "floor")),
            EmojiEntry("😊", listOf("blush", "smile", "happy")),
            EmojiEntry("😇", listOf("angel", "smile", "halo")),
            EmojiEntry("🙂", listOf("smile", "slight", "face")),
            EmojiEntry("🙃", listOf("upside", "down", "face")),
            EmojiEntry("😉", listOf("wink", "face")),
            EmojiEntry("😍", listOf("heart", "eyes", "love")),
            EmojiEntry("🥰", listOf("love", "heart", "smile")),
            EmojiEntry("😘", listOf("kiss", "heart", "blow")),
            EmojiEntry("😋", listOf("yum", "tongue", "tasty")),
            EmojiEntry("😎", listOf("cool", "sunglasses")),
            EmojiEntry("🤓", listOf("nerd", "glasses")),
            EmojiEntry("🤩", listOf("star", "eyes", "excited")),
            EmojiEntry("🥳", listOf("party", "celebrate")),
            EmojiEntry("😏", listOf("smirk", "smug")),
            EmojiEntry("😒", listOf("unamused", "meh")),
            EmojiEntry("😞", listOf("disappointed", "sad")),
            EmojiEntry("😔", listOf("pensive", "sad")),
            EmojiEntry("😟", listOf("worried", "concern")),
            EmojiEntry("😕", listOf("confused", "meh")),
            EmojiEntry("😣", listOf("persevere", "struggle")),
            EmojiEntry("😖", listOf("confounded", "frustrated")),
            EmojiEntry("😫", listOf("tired", "frustrated")),
            EmojiEntry("😩", listOf("weary", "tired")),
            EmojiEntry("🥺", listOf("plead", "puppy", "eyes")),
            EmojiEntry("😢", listOf("cry", "tear", "sad")),
            EmojiEntry("😭", listOf("sob", "cry", "tears")),
            EmojiEntry("😤", listOf("triumph", "angry", "nose")),
            EmojiEntry("😠", listOf("angry", "mad")),
            EmojiEntry("😡", listOf("rage", "pout", "angry")),
            EmojiEntry("🤬", listOf("cursing", "angry", "symbols")),
            EmojiEntry("🤯", listOf("exploding", "head", "shock")),
            EmojiEntry("😳", listOf("flushed", "embarrassed")),
            EmojiEntry("🥵", listOf("hot", "sweating", "heat")),
            EmojiEntry("🥶", listOf("cold", "freezing", "ice")),
            EmojiEntry("😱", listOf("scream", "fear", "shock")),
            EmojiEntry("😨", listOf("fearful", "scared")),
            EmojiEntry("😰", listOf("anxious", "sweat", "cold")),
            EmojiEntry("😥", listOf("disappointed", "relieved", "sweat")),
            EmojiEntry("😓", listOf("sweat", "droop", "worried")),
            EmojiEntry("🤗", listOf("hug", "hands", "open")),
            EmojiEntry("🤔", listOf("thinking", "hmm", "thought")),
            EmojiEntry("🤭", listOf("hand", "over", "mouth", "giggle")),
            EmojiEntry("🤫", listOf("shh", "shush", "quiet")),
            EmojiEntry("🤥", listOf("liar", "lying", "nose")),
            EmojiEntry("😐", listOf("neutral", "meh", "face")),
            EmojiEntry("😑", listOf("expressionless", "blank")),
            EmojiEntry("😬", listOf("grimace", "awkward", "teeth")),
            EmojiEntry("🙄", listOf("roll", "eyes")),
            EmojiEntry("😯", listOf("hushed", "surprised")),
            EmojiEntry("😦", listOf("frowning", "open", "mouth")),
            EmojiEntry("😧", listOf("anguished", "distressed")),
            EmojiEntry("😮", listOf("surprised", "open", "mouth")),
            EmojiEntry("😲", listOf("astonished", "shock")),
            EmojiEntry("🥱", listOf("yawn", "tired", "sleepy")),
            EmojiEntry("😴", listOf("sleeping", "zzz")),
            EmojiEntry("🤤", listOf("drool", "saliva")),
            EmojiEntry("😪", listOf("sleepy", "tired")),
            EmojiEntry("😵", listOf("dizzy", "confused")),
            EmojiEntry("🤐", listOf("zipper", "mouth", "silent")),
            EmojiEntry("🥴", listOf("woozy", "dizzy")),
            EmojiEntry("🤢", listOf("nauseated", "sick", "vomit")),
            EmojiEntry("🤮", listOf("vomit", "sick")),
            EmojiEntry("🤧", listOf("sneeze", "sick")),
            EmojiEntry("😷", listOf("mask", "sick", "medical")),
            EmojiEntry("🤒", listOf("thermometer", "sick", "fever")),
            EmojiEntry("🤕", listOf("head", "bandage", "hurt")),
            EmojiEntry("🤑", listOf("money", "mouth", "rich")),
            EmojiEntry("🤠", listOf("cowboy", "hat")),
            EmojiEntry("😈", listOf("smiling", "imp", "devil")),
            EmojiEntry("👿", listOf("angry", "devil", "imp")),
            EmojiEntry("👻", listOf("ghost", "boo")),
            EmojiEntry("💀", listOf("skull", "dead")),
            EmojiEntry("🤖", listOf("robot", "machine")),
            EmojiEntry("👽", listOf("alien", "space")),
            EmojiEntry("🎃", listOf("pumpkin", "halloween")),
            EmojiEntry("😺", listOf("cat", "smile")),
            EmojiEntry("🙈", listOf("see", "no", "evil", "monkey")),
        )),
        EmojiCategory("animals", "🐶", listOf(
            EmojiEntry("🐶", listOf("dog", "puppy", "pet")),
            EmojiEntry("🐱", listOf("cat", "kitty", "pet")),
            EmojiEntry("🐭", listOf("mouse", "rodent")),
            EmojiEntry("🐹", listOf("hamster")),
            EmojiEntry("🐰", listOf("rabbit", "bunny")),
            EmojiEntry("🦊", listOf("fox")),
            EmojiEntry("🐻", listOf("bear")),
            EmojiEntry("🐼", listOf("panda")),
            EmojiEntry("🐨", listOf("koala")),
            EmojiEntry("🐯", listOf("tiger")),
            EmojiEntry("🦁", listOf("lion")),
            EmojiEntry("🐮", listOf("cow")),
            EmojiEntry("🐷", listOf("pig")),
            EmojiEntry("🐸", listOf("frog")),
            EmojiEntry("🐵", listOf("monkey")),
            EmojiEntry("🐔", listOf("chicken", "hen")),
            EmojiEntry("🐧", listOf("penguin")),
            EmojiEntry("🐦", listOf("bird")),
            EmojiEntry("🐤", listOf("chick", "baby")),
            EmojiEntry("🦆", listOf("duck")),
            EmojiEntry("🦅", listOf("eagle")),
            EmojiEntry("🦉", listOf("owl")),
            EmojiEntry("🦇", listOf("bat")),
            EmojiEntry("🐺", listOf("wolf")),
            EmojiEntry("🐗", listOf("boar", "pig")),
            EmojiEntry("🐴", listOf("horse")),
            EmojiEntry("🦄", listOf("unicorn")),
            EmojiEntry("🐝", listOf("bee", "honey")),
            EmojiEntry("🐛", listOf("bug", "caterpillar")),
            EmojiEntry("🦋", listOf("butterfly")),
            EmojiEntry("🐌", listOf("snail")),
            EmojiEntry("🐞", listOf("ladybug", "beetle")),
            EmojiEntry("🐢", listOf("turtle")),
            EmojiEntry("🐍", listOf("snake")),
            EmojiEntry("🦎", listOf("lizard")),
            EmojiEntry("🦖", listOf("t", "rex", "dinosaur")),
            EmojiEntry("🦕", listOf("sauropod", "dinosaur")),
            EmojiEntry("🐙", listOf("octopus")),
            EmojiEntry("🦑", listOf("squid")),
            EmojiEntry("🦞", listOf("lobster")),
            EmojiEntry("🦀", listOf("crab")),
            EmojiEntry("🐳", listOf("whale")),
            EmojiEntry("🐬", listOf("dolphin")),
            EmojiEntry("🐟", listOf("fish")),
            EmojiEntry("🐠", listOf("tropical", "fish")),
            EmojiEntry("🐡", listOf("blowfish")),
            EmojiEntry("🦈", listOf("shark")),
            EmojiEntry("🐊", listOf("crocodile")),
        )),
        EmojiCategory("nature", "🌸", listOf(
            EmojiEntry("🌸", listOf("cherry", "blossom", "flower")),
            EmojiEntry("🌺", listOf("hibiscus", "flower")),
            EmojiEntry("🌻", listOf("sunflower")),
            EmojiEntry("🌹", listOf("rose", "flower")),
            EmojiEntry("🌷", listOf("tulip", "flower")),
            EmojiEntry("🌼", listOf("blossom", "flower")),
            EmojiEntry("💐", listOf("bouquet", "flowers")),
            EmojiEntry("🍀", listOf("clover", "lucky")),
            EmojiEntry("🍁", listOf("maple", "leaf", "autumn")),
            EmojiEntry("🍂", listOf("fallen", "leaf", "autumn")),
            EmojiEntry("🌿", listOf("herb", "leaf")),
            EmojiEntry("🌱", listOf("seedling", "sprout")),
            EmojiEntry("🌳", listOf("tree", "deciduous")),
            EmojiEntry("🌴", listOf("palm", "tree")),
            EmojiEntry("🌲", listOf("pine", "evergreen", "tree")),
            EmojiEntry("🌵", listOf("cactus")),
            EmojiEntry("🌾", listOf("rice", "sheaf")),
            EmojiEntry("☀️", listOf("sun", "sunny")),
            EmojiEntry("🌈", listOf("rainbow")),
            EmojiEntry("⛅", listOf("sun", "behind", "cloud")),
            EmojiEntry("🌧️", listOf("cloud", "rain")),
            EmojiEntry("⛈️", listOf("thunder", "storm")),
            EmojiEntry("❄️", listOf("snowflake", "snow")),
            EmojiEntry("☃️", listOf("snowman")),
            EmojiEntry("🌊", listOf("ocean", "wave")),
            EmojiEntry("🌋", listOf("volcano")),
            EmojiEntry("🌍", listOf("earth", "globe", "world")),
            EmojiEntry("🌙", listOf("moon", "crescent")),
            EmojiEntry("⭐", listOf("star")),
            EmojiEntry("🌟", listOf("glowing", "star", "sparkle")),
            EmojiEntry("✨", listOf("sparkles", "sparkle")),
            EmojiEntry("💫", listOf("dizzy", "star")),
            EmojiEntry("💥", listOf("collision", "explosion", "boom")),
            EmojiEntry("🔥", listOf("fire", "flame", "hot")),
        )),
        EmojiCategory("food", "🍕", listOf(
            EmojiEntry("🍏", listOf("green", "apple")),
            EmojiEntry("🍎", listOf("apple", "red")),
            EmojiEntry("🍌", listOf("banana")),
            EmojiEntry("🍇", listOf("grapes")),
            EmojiEntry("🍊", listOf("orange", "tangerine")),
            EmojiEntry("🍋", listOf("lemon")),
            EmojiEntry("🍑", listOf("peach")),
            EmojiEntry("🍓", listOf("strawberry")),
            EmojiEntry("🍒", listOf("cherries")),
            EmojiEntry("🍍", listOf("pineapple")),
            EmojiEntry("🥭", listOf("mango")),
            EmojiEntry("🥑", listOf("avocado")),
            EmojiEntry("🥕", listOf("carrot")),
            EmojiEntry("🌽", listOf("corn", "maize")),
            EmojiEntry("🥦", listOf("broccoli")),
            EmojiEntry("🍄", listOf("mushroom")),
            EmojiEntry("🥜", listOf("peanuts")),
            EmojiEntry("🍕", listOf("pizza")),
            EmojiEntry("🍔", listOf("burger", "hamburger")),
            EmojiEntry("🍟", listOf("fries", "french")),
            EmojiEntry("🌭", listOf("hot", "dog")),
            EmojiEntry("🍿", listOf("popcorn")),
            EmojiEntry("🥓", listOf("bacon")),
            EmojiEntry("🍗", listOf("chicken", "drumstick")),
            EmojiEntry("🍖", listOf("meat", "bone")),
            EmojiEntry("🥩", listOf("steak", "meat")),
            EmojiEntry("🍜", listOf("noodles", "ramen")),
            EmojiEntry("🍣", listOf("sushi")),
            EmojiEntry("🍤", listOf("shrimp", "fried")),
            EmojiEntry("🥟", listOf("dumpling")),
            EmojiEntry("🍚", listOf("rice", "cooked")),
            EmojiEntry("🍰", listOf("cake", "shortcake")),
            EmojiEntry("🎂", listOf("birthday", "cake")),
            EmojiEntry("🍪", listOf("cookie")),
            EmojiEntry("🍩", listOf("donut", "doughnut")),
            EmojiEntry("🍫", listOf("chocolate", "bar")),
            EmojiEntry("🍬", listOf("candy")),
            EmojiEntry("🍭", listOf("lollipop")),
            EmojiEntry("☕", listOf("coffee", "tea")),
            EmojiEntry("🍵", listOf("tea", "matcha")),
            EmojiEntry("🧋", listOf("bubble", "tea", "boba")),
            EmojiEntry("🍺", listOf("beer")),
            EmojiEntry("🍷", listOf("wine")),
            EmojiEntry("🥂", listOf("champagne", "cheers", "clink")),
            EmojiEntry("🧊", listOf("ice", "cube")),
        )),
        EmojiCategory("activity", "⚽", listOf(
            EmojiEntry("⚽", listOf("soccer", "football")),
            EmojiEntry("🏀", listOf("basketball")),
            EmojiEntry("🏈", listOf("american", "football")),
            EmojiEntry("⚾", listOf("baseball")),
            EmojiEntry("🎾", listOf("tennis")),
            EmojiEntry("🏐", listOf("volleyball")),
            EmojiEntry("🏓", listOf("ping", "pong", "table")),
            EmojiEntry("🏸", listOf("badminton")),
            EmojiEntry("🥊", listOf("boxing", "glove")),
            EmojiEntry("🥋", listOf("martial", "arts")),
            EmojiEntry("⛳", listOf("golf", "flag", "hole")),
            EmojiEntry("🏹", listOf("archery", "bow", "arrow")),
            EmojiEntry("⛸️", listOf("skate", "ice")),
            EmojiEntry("⛷️", listOf("skier", "ski")),
            EmojiEntry("🏂", listOf("snowboarder")),
            EmojiEntry("🏊", listOf("swimmer", "swim")),
            EmojiEntry("🚴", listOf("cyclist", "bike")),
            EmojiEntry("🏇", listOf("horse", "racing")),
            EmojiEntry("🧗", listOf("climbing")),
            EmojiEntry("🏋️", listOf("weight", "lifting")),
            EmojiEntry("🎯", listOf("target", "dart")),
            EmojiEntry("🎮", listOf("video", "game", "controller")),
            EmojiEntry("🎲", listOf("dice", "game")),
            EmojiEntry("🎰", listOf("slot", "machine")),
            EmojiEntry("🧩", listOf("puzzle", "piece")),
            EmojiEntry("🎨", listOf("art", "palette")),
            EmojiEntry("🎭", listOf("theater", "masks")),
            EmojiEntry("🎬", listOf("film", "movie", "clapper")),
            EmojiEntry("🎤", listOf("microphone", "mic")),
            EmojiEntry("🎧", listOf("headphones", "earphone")),
            EmojiEntry("🎹", listOf("musical", "keyboard", "piano")),
            EmojiEntry("🥁", listOf("drum")),
            EmojiEntry("🎸", listOf("guitar")),
            EmojiEntry("🎺", listOf("trumpet")),
            EmojiEntry("🎻", listOf("violin")),
            EmojiEntry("📚", listOf("books", "study")),
            EmojiEntry("📖", listOf("book", "open")),
            EmojiEntry("✏️", listOf("pencil", "writing")),
            EmojiEntry("📝", listOf("memo", "note")),
            EmojiEntry("🧠", listOf("brain", "mind")),
        )),
        EmojiCategory("travel", "✈️", listOf(
            EmojiEntry("🚗", listOf("car", "automobile")),
            EmojiEntry("🚕", listOf("taxi")),
            EmojiEntry("🚙", listOf("suv", "car")),
            EmojiEntry("🚌", listOf("bus")),
            EmojiEntry("🏎️", listOf("racing", "car")),
            EmojiEntry("🚓", listOf("police", "car")),
            EmojiEntry("🚑", listOf("ambulance")),
            EmojiEntry("🚒", listOf("fire", "engine")),
            EmojiEntry("🚜", listOf("tractor")),
            EmojiEntry("🏍️", listOf("motorcycle", "bike")),
            EmojiEntry("🚲", listOf("bicycle", "bike")),
            EmojiEntry("✈️", listOf("airplane", "plane", "flight")),
            EmojiEntry("🚀", listOf("rocket", "space")),
            EmojiEntry("🛸", listOf("ufo", "flying", "saucer")),
            EmojiEntry("🚁", listOf("helicopter")),
            EmojiEntry("🛶", listOf("canoe")),
            EmojiEntry("⛵", listOf("sailboat", "sail")),
            EmojiEntry("🚢", listOf("ship")),
            EmojiEntry("🚂", listOf("locomotive", "steam", "train")),
            EmojiEntry("🚄", listOf("bullet", "train")),
            EmojiEntry("🚇", listOf("metro", "subway")),
            EmojiEntry("🗺️", listOf("world", "map")),
            EmojiEntry("🏝️", listOf("desert", "island")),
            EmojiEntry("🏖️", listOf("beach", "umbrella")),
            EmojiEntry("🏔️", listOf("mountain", "snow")),
            EmojiEntry("🏕️", listOf("camping", "tent")),
            EmojiEntry("🏠", listOf("house", "home")),
            EmojiEntry("🏡", listOf("house", "garden", "home")),
            EmojiEntry("🏢", listOf("office", "building")),
            EmojiEntry("🏨", listOf("hotel")),
            EmojiEntry("🏪", listOf("convenience", "store")),
            EmojiEntry("🏫", listOf("school")),
            EmojiEntry("🏛️", listOf("classical", "building")),
            EmojiEntry("🌆", listOf("city", "sunset")),
            EmojiEntry("🗼", listOf("tokyo", "tower")),
            EmojiEntry("🌉", listOf("bridge", "night")),
        )),
        EmojiCategory("objects", "💡", listOf(
            EmojiEntry("💡", listOf("bulb", "light", "idea")),
            EmojiEntry("🔦", listOf("flashlight", "torch")),
            EmojiEntry("🔥", listOf("fire", "flame")),
            EmojiEntry("🧯", listOf("extinguisher", "fire")),
            EmojiEntry("🛠️", listOf("hammer", "wrench", "tools")),
            EmojiEntry("🔧", listOf("wrench", "tool")),
            EmojiEntry("🔩", listOf("nut", "bolt")),
            EmojiEntry("⚙️", listOf("gear", "mechanical")),
            EmojiEntry("🔨", listOf("hammer")),
            EmojiEntry("🔪", listOf("kitchen", "knife")),
            EmojiEntry("🛡️", listOf("shield")),
            EmojiEntry("🔒", listOf("lock", "closed")),
            EmojiEntry("🔓", listOf("unlock", "open")),
            EmojiEntry("🔑", listOf("key")),
            EmojiEntry("🗝️", listOf("old", "key")),
            EmojiEntry("📁", listOf("folder", "closed")),
            EmojiEntry("📂", listOf("folder", "open")),
            EmojiEntry("🗂️", listOf("card", "index", "dividers")),
            EmojiEntry("📜", listOf("scroll")),
            EmojiEntry("📊", listOf("chart", "bar", "stats")),
            EmojiEntry("📈", listOf("chart", "trending", "up")),
            EmojiEntry("📉", listOf("chart", "down")),
            EmojiEntry("🔍", listOf("magnifying", "glass", "search")),
            EmojiEntry("🔎", listOf("magnifying", "glass", "right")),
            EmojiEntry("📌", listOf("pushpin")),
            EmojiEntry("📍", listOf("round", "pushpin", "location")),
            EmojiEntry("🗑️", listOf("wastebasket", "trash")),
            EmojiEntry("🖇️", listOf("paperclip")),
            EmojiEntry("📎", listOf("paperclip")),
            EmojiEntry("✂️", listOf("scissors")),
            EmojiEntry("📏", listOf("straight", "ruler")),
            EmojiEntry("📐", listOf("triangular", "ruler")),
            EmojiEntry("🧲", listOf("magnet")),
            EmojiEntry("⚗️", listOf("alembic", "chemistry")),
            EmojiEntry("🧪", listOf("test", "tube", "science")),
            EmojiEntry("🔬", listOf("microscope", "science")),
            EmojiEntry("🔭", listOf("telescope")),
            EmojiEntry("📡", listOf("satellite", "antenna")),
            EmojiEntry("💻", listOf("laptop", "computer")),
            EmojiEntry("🖥️", listOf("desktop", "computer")),
            EmojiEntry("⌨️", listOf("keyboard")),
            EmojiEntry("📱", listOf("phone", "smartphone", "mobile")),
            EmojiEntry("📲", listOf("phone", "calling")),
            EmojiEntry("🕹️", listOf("joystick")),
            EmojiEntry("🎁", listOf("gift", "present")),
            EmojiEntry("🏆", listOf("trophy", "award")),
            EmojiEntry("🥇", listOf("gold", "medal")),
            EmojiEntry("🥈", listOf("silver", "medal")),
            EmojiEntry("🥉", listOf("bronze", "medal")),
            EmojiEntry("💎", listOf("gem", "diamond")),
            EmojiEntry("💰", listOf("money", "bag")),
            EmojiEntry("💳", listOf("credit", "card")),
        )),
        EmojiCategory("symbols", "❤️", listOf(
            EmojiEntry("❤️", listOf("heart")),
            EmojiEntry("🧡", listOf("orange", "heart")),
            EmojiEntry("💛", listOf("yellow", "heart")),
            EmojiEntry("💚", listOf("green", "heart")),
            EmojiEntry("💙", listOf("blue", "heart")),
            EmojiEntry("💜", listOf("purple", "heart")),
            EmojiEntry("🖤", listOf("black", "heart")),
            EmojiEntry("🤍", listOf("white", "heart")),
            EmojiEntry("💔", listOf("broken", "heart")),
            EmojiEntry("💕", listOf("two", "hearts")),
            EmojiEntry("💞", listOf("revolving", "hearts")),
            EmojiEntry("💓", listOf("beating", "heart")),
            EmojiEntry("💗", listOf("growing", "heart")),
            EmojiEntry("💖", listOf("sparkling", "heart")),
            EmojiEntry("💘", listOf("cupid", "arrow", "heart")),
            EmojiEntry("💝", listOf("heart", "ribbon", "gift")),
            EmojiEntry("✅", listOf("check", "mark", "done")),
            EmojiEntry("☑️", listOf("check", "box")),
            EmojiEntry("✔️", listOf("heavy", "check")),
            EmojiEntry("❌", listOf("cross", "wrong", "no")),
            EmojiEntry("❎", listOf("cross", "button")),
            EmojiEntry("⭕", listOf("o", "circle")),
            EmojiEntry("🔴", listOf("red", "circle")),
            EmojiEntry("🟢", listOf("green", "circle")),
            EmojiEntry("🔵", listOf("blue", "circle")),
            EmojiEntry("🟡", listOf("yellow", "circle")),
            EmojiEntry("⚪", listOf("white", "circle")),
            EmojiEntry("⚫", listOf("black", "circle")),
            EmojiEntry("➕", listOf("plus", "add")),
            EmojiEntry("➖", listOf("minus")),
            EmojiEntry("➗", listOf("divide", "division")),
            EmojiEntry("✖️", listOf("multiply", "times")),
            EmojiEntry("❗", listOf("exclamation", "bang")),
            EmojiEntry("❓", listOf("question")),
            EmojiEntry("⚠️", listOf("warning")),
            EmojiEntry("🚨", listOf("alarm", "siren")),
            EmojiEntry("⛔", listOf("no", "entry", "prohibited")),
            EmojiEntry("🚫", listOf("prohibited", "no")),
            EmojiEntry("🚦", listOf("traffic", "light")),
            EmojiEntry("💬", listOf("speech", "bubble", "chat")),
            EmojiEntry("💭", listOf("thought", "balloon")),
            EmojiEntry("🗨️", listOf("left", "speech", "bubble")),
            EmojiEntry("📢", listOf("loudspeaker")),
            EmojiEntry("📣", listOf("megaphone")),
            EmojiEntry("🔔", listOf("bell", "ring")),
            EmojiEntry("🔕", listOf("bell", "with", "slash")),
            EmojiEntry("♻️", listOf("recycle", "green")),
            EmojiEntry("⭐", listOf("star", "rating")),
            EmojiEntry("🌟", listOf("glowing", "star")),
            EmojiEntry("💫", listOf("dizzy", "sparkle")),
            EmojiEntry("⚡", listOf("high", "voltage", "zap")),
            EmojiEntry("💧", listOf("droplet", "water")),
            EmojiEntry("💤", listOf("zzz", "sleep")),
            EmojiEntry("♠️", listOf("spade")),
            EmojiEntry("♥️", listOf("heart", "suit")),
            EmojiEntry("♦️", listOf("diamond", "suit")),
            EmojiEntry("♣️", listOf("club", "suit")),
            EmojiEntry("🎵", listOf("musical", "note")),
            EmojiEntry("🎶", listOf("musical", "notes")),
            EmojiEntry("📶", listOf("signal", "bars")),
        )),
    )

    fun search(query: String): List<EmojiEntry> {
        val q = query.lowercase()
        if (q.isBlank()) return emptyList()
        return categories.flatMap { c -> c.entries }.filter { e ->
            e.emoji.contains(q) || e.tags.any { it.contains(q) }
        }
    }
}

/**
 * A labeled clickable field that shows the currently selected emoji and opens
 * the built-in emoji picker dialog on click.
 */
@Composable
fun EmojiPickerField(
    icon: String,
    onIconChange: (String) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    val strings = koinInject<I18nManager>().strings
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
        }
        Surface(
            onClick = { showDialog = true },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(icon.ifBlank { "📁" }, fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    strings.emojiChoose,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDialog) {
        EmojiPickerDialog(
            current = icon.ifBlank { "📁" },
            onDismiss = { showDialog = false },
            onPick = { emoji ->
                onIconChange(emoji)
                showDialog = false
            },
        )
    }
}

@Composable
private fun EmojiPickerDialog(
    current: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val strings = koinInject<I18nManager>().strings
    var query by remember { mutableStateOf("") }
    var categoryKey by remember { mutableStateOf(EmojiLibrary.categories.first().key) }

    val currentCategory = EmojiLibrary.categories.firstOrNull { it.key == categoryKey }
    val results = remember(query, categoryKey) {
        if (query.isBlank()) currentCategory?.entries.orEmpty() else EmojiLibrary.search(query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.emojiChoose) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(strings.emojiSearchHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (query.isBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        EmojiLibrary.categories.forEach { cat ->
                            val selected = cat.key == categoryKey
                            Surface(
                                onClick = { categoryKey = cat.key },
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(cat.sample, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (query.isBlank()) currentCategory?.sample.orEmpty() else strings.actionSearch,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (results.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(strings.settingsSearchNoResults, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(results) { entry ->
                            val selected = entry.emoji == current
                            Surface(
                                onClick = { onPick(entry.emoji) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(entry.emoji, fontSize = 22.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.actionClose) }
        },
    )
}
