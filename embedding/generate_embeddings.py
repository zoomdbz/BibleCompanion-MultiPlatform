#!/usr/bin/env python3
"""
Step 2: Generate embeddings for all corpus entries using multilingual-e5-small.

Reads output/corpus_{lang}.jsonl files, embeds each entry's text,
exports int8 quantized binary matrices and metadata indexes.

Output per language:
  output/embeddings_{lang}.bin   -- int8 quantized vectors (N x 384)
  output/metadata_{lang}.json    -- maps vector index -> entry metadata

Usage:
  python generate_embeddings.py              # all languages
  python generate_embeddings.py en es ar     # specific languages
"""

import json
import struct
import sys
import time
from pathlib import Path

import numpy as np
import torch

from embedder import Embedder

OUTPUT_DIR = Path(__file__).parent / "output"
MODEL_NAME = "intfloat/multilingual-e5-small"
BATCH_SIZE = 256
EMBEDDING_DIM = 384

LANGUAGES = ["en", "ar", "de", "es", "fr", "hi", "it", "ja", "ko", "pt", "ru", "zh-Hans", "zh-Hant"]

CANONICAL_NAMES = {
    "genesis-1": "The Creation",
    "genesis-2": "The Garden of Eden, Adam and Eve, The First Marriage, A Man Shall Leave His Father and Mother and Cleave to His Wife",
    "genesis-3": "The Fall of Man, Original Sin, The Serpent's Temptation, The Talking Snake in the Garden of Eden, The Snake That Talked to Eve, Dust Thou Art and Unto Dust Shalt Thou Return",
    "genesis-4": "Cain and Abel, The First Murder",
    "genesis-6": "Noah's Ark, The Great Flood",
    "genesis-7": "Noah's Ark, The Great Flood",
    "genesis-8": "Noah's Ark, The Flood Recedes",
    "genesis-9": "The Covenant of the Rainbow",
    "genesis-11": "The Tower of Babel",
    "genesis-12": "The Call of Abram",
    "genesis-15": "God's Covenant with Abraham",
    "genesis-17": "The Covenant of Circumcision",
    "genesis-18": "Abraham's Three Visitors, Sodom's Judgment",
    "genesis-19": "The Destruction of Sodom and Gomorrah, Lot's Wife Turned to a Pillar of Salt",
    "genesis-21": "The Birth of Isaac",
    "genesis-5": "The Genealogy of Adam, Methuselah the Oldest Man, The Generations Before the Flood",
    "genesis-22": "The Binding of Isaac, The Sacrifice of Isaac, The Aqedah",
    "genesis-25": "Jacob and Esau, The Birthright",
    "genesis-27": "Jacob Steals Esau's Blessing",
    "genesis-28": "Jacob's Ladder, Jacob's Dream at Bethel",
    "genesis-32": "Jacob Wrestles with God",
    "genesis-37": "Joseph's Coat of Many Colors, Joseph Sold into Slavery",
    "genesis-39": "Joseph and Potiphar's Wife",
    "genesis-40": "Joseph Interprets Dreams in Prison",
    "genesis-41": "Pharaoh's Dream, Joseph Rises to Power",
    "genesis-45": "Joseph Reveals Himself to His Brothers",
    "exodus-1": "The Israelites Enslaved in Egypt",
    "exodus-2": "The Birth of Moses, Moses in the Bulrushes",
    "exodus-3": "The Burning Bush, God Calls Moses",
    "exodus-4": "God Sends Moses to Egypt, Moses' Staff Becomes a Serpent, God Hardens Pharaoh's Heart",
    "exodus-7": "The Plagues of Egypt, Water Turned to Blood, God Hardens Pharaoh's Heart",
    "exodus-8": "The Plagues of Egypt, Frogs Gnats and Flies",
    "exodus-9": "The Plagues of Egypt, Livestock Disease Boils and Hail",
    "exodus-10": "The Plagues of Egypt, Locusts and Darkness",
    "exodus-11": "The Plague of the Firstborn, The Death of the Firstborn, God Hardens Pharaoh's Heart",
    "exodus-12": "The Passover, The First Passover Lamb",
    "exodus-14": "The Parting of the Red Sea, The Crossing of the Red Sea",
    "exodus-16": "Manna from Heaven",
    "exodus-17": "Water from the Rock, Battle with the Amalekites",
    "exodus-20": "The Ten Commandments, The Law Given at Sinai, God's Laws Written on Stone Tablets at Mount Sinai, Thou Shalt Have No Other Gods",
    "exodus-21": "An Eye for an Eye, Laws about Servants and Personal Injury, Hebrew Civil Law",
    "exodus-32": "The Golden Calf Idol",
    "exodus-33": "Moses Sees God's Glory",
    "leviticus-16": "The Day of Atonement, Yom Kippur",
    "leviticus-23": "The Feasts of the Lord, The Biblical Feast Calendar",
    "numbers-13": "The Twelve Spies Sent to Canaan",
    "numbers-14": "Israel Refuses to Enter the Promised Land",
    "numbers-17": "Aaron's Rod That Budded",
    "numbers-20": "Moses Strikes the Rock",
    "numbers-22": "Balaam and His Talking Donkey, Balaam's Donkey",
    "deuteronomy-5": "The Ten Commandments Restated by Moses, The Ten Commandments Given at Horeb, Do Not Murder Do Not Steal Do Not Commit Adultery",
    "deuteronomy-10": "The Second Tablets of Stone, The Ark of the Covenant Built, Circumcise Your Hearts, The Levites Set Apart",
    "deuteronomy-34": "The Death and Burial of Moses",
    "joshua-1": "Be Strong and Courageous, God Commissions Joshua to Lead Israel",
    "joshua-2": "Rahab and the Spies, Rahab Hiding the Spies",
    "joshua-3": "The Crossing of the Jordan River",
    "joshua-6": "The Battle of Jericho, The Walls of Jericho Falling Down",
    "joshua-9": "The Gibeonite Deception, Israel Makes a Treaty with Gibeon",
    "joshua-10": "The Sun Standing Still, Joshua's Long Day, The Sun Stopped in the Middle of the Sky",
    "judges-3": "Ehud the Left-Handed Judge, Othniel, Shamgar",
    "judges-4": "Deborah and Barak, Jael Drives the Tent Peg into Sisera's Temple",
    "judges-6": "Gideon's Fleece",
    "judges-7": "Gideon's Three Hundred",
    "judges-13": "The Birth of Samson",
    "judges-14": "Samson and the Lion, Samson's Riddle",
    "judges-15": "Samson and the Jawbone of a Donkey",
    "judges-16": "Samson and Delilah, Samson's Death, The Strongest Man in the Bible",
    "ruth-1": "Ruth and Naomi",
    "ruth-2": "Ruth Gleaning in the Fields of Boaz",
    "ruth-4": "Boaz Redeems Ruth, The Kinsman Redeemer",
    "1-samuel-28": "The Witch of Endor, Saul Consults a Medium, The Spirit of Samuel",
    "1-samuel-1": "Hannah's Prayer for a Son",
    "1-samuel-3": "God Calls Samuel",
    "1-samuel-5": "The Ark of the Covenant Captured by the Philistines",
    "1-samuel-16": "David Anointed King, David Plays the Harp for Saul",
    "1-samuel-17": "David and Goliath, David Kills Goliath, The Shepherd Boy Who Killed the Giant with a Sling and a Stone, A Kid Kills a Giant",
    "1-samuel-18": "David and Jonathan's Friendship",
    "1-samuel-24": "David Spares Saul's Life in the Cave",
    "2-samuel-6": "David Dances Before the Ark",
    "2-samuel-11": "David and Bathsheba",
    "2-samuel-12": "Nathan Confronts David, The Parable of the Ewe Lamb",
    "1-kings-3": "Solomon Asks God for Wisdom, The Wisdom of Solomon",
    "1-kings-6": "Solomon Builds the Temple",
    "1-kings-10": "The Queen of Sheba Visits Solomon",
    "1-kings-17": "Elijah Fed by Ravens",
    "1-kings-18": "Elijah and the Prophets of Baal on Mount Carmel, Elijah Calls Down Fire from Heaven on the Altar, The Contest on Mount Carmel",
    "1-kings-19": "Elijah Flees to Horeb, The Still Small Voice",
    "2-kings-2": "Elijah Taken Up in a Chariot of Fire, Elisha and the Bears",
    "2-kings-4": "Elisha and the Widow's Oil, The Shunammite Woman's Son Raised",
    "2-kings-5": "Naaman Healed of Leprosy",
    "2-kings-6": "The Floating Axe Head, Elisha's Invisible Army",
    "2-kings-20": "Hezekiah's Prayer and Healing, The Sundial Goes Backward",
    "2-kings-22": "Josiah Finds the Book of the Law",
    "job-1": "The Testing of Job, Satan Afflicts Job",
    "job-38": "God Speaks to Job from the Whirlwind",
    "psalms-23": "The Lord Is My Shepherd, The Shepherd's Psalm, The Valley of the Shadow of Death, Yea Though I Walk Through the Valley",
    "psalms-51": "David's Prayer of Repentance",
    "psalms-91": "The Psalm of Protection",
    "psalms-106": "Israel's Rebellion Recalled, The Golden Calf, The Parting of the Red Sea, The Plagues of Egypt, Balaam, Israel's Unfaithfulness",
    "psalms-119": "The Longest Psalm, The Word of God",
    "psalms-137": "By the Rivers of Babylon",
    "isaiah-6": "Isaiah's Vision of God's Throne",
    "isaiah-7": "The Virgin Shall Conceive, The Immanuel Prophecy",
    "isaiah-9": "For unto Us a Child Is Born",
    "isaiah-14": "The Fall of Lucifer, How You Have Fallen from Heaven, Satan Cast Down",
    "isaiah-40": "Comfort Ye My People",
    "isaiah-41": "Fear Not for I Am with You, Do Not Be Dismayed for I Am Your God, I Will Strengthen You and Help You",
    "isaiah-45": "Every Knee Shall Bow, I Am the Lord and There Is None Else, There Is No God Besides Me",
    "isaiah-48": "God's Sovereign Call to Israel, Israel's Stubbornness and Refinement",
    "isaiah-52": "The Suffering Servant",
    "isaiah-53": "The Suffering Servant, He Was Pierced for Our Transgressions",
    "jeremiah-29": "Plans to Prosper You, The Letter to the Exiles",
    "ezekiel-1": "Ezekiel's Vision of the Throne of God, The Four Living Creatures",
    "ezekiel-37": "The Valley of Dry Bones",
    "daniel-1": "Daniel and His Friends Refuse the King's Food",
    "daniel-2": "Nebuchadnezzar's Dream, The Statue of Four Kingdoms",
    "daniel-3": "The Fiery Furnace, Shadrach Meshach and Abednego Thrown into the Fiery Furnace by King Nebuchadnezzar, The Fourth Man in the Fire",
    "daniel-4": "Nebuchadnezzar's Madness, The King Loses His Mind and Lives Like an Animal, Seven Years of Insanity",
    "daniel-5": "The Handwriting on the Wall, Belshazzar's Feast",
    "daniel-6": "Daniel in the Lions' Den",
    "daniel-7": "The Vision of the Four Beasts",
    "daniel-9": "Daniel's Prayer of Repentance, The Seventy Weeks Prophecy, The Abomination of Desolation, Messiah Shall Be Cut Off",
    "jonah-1": "Jonah Flees from God, Jonah Swallowed by a Great Fish",
    "jonah-2": "Jonah's Prayer from Inside the Fish",
    "jonah-3": "Nineveh Repents",
    "matthew-1": "The Genealogy of Jesus, The Birth of Jesus Christ",
    "matthew-2": "The Birth of Jesus, The Visit of the Magi, The Wise Men, The Star of Bethlehem, The Flight to Egypt, The Massacre of the Innocents",
    "matthew-3": "The Baptism of Jesus by John the Baptist",
    "matthew-4": "The Temptation of Jesus in the Wilderness",
    "matthew-5": "The Sermon on the Mount, The Beatitudes",
    "matthew-6": "The Lord's Prayer, Do Not Worry, Take No Thought for Your Life, Seek First the Kingdom of God, Bible Verses About Anxiety and Worry",
    "matthew-8": "Jesus Calms the Storm, The Centurion's Faith",
    "matthew-10": "Jesus Sends Out the Twelve Apostles",
    "matthew-13": "The Parables of the Kingdom, The Sower and the Seed",
    "matthew-14": "Feeding the Five Thousand, Walking on Water, The Death of John the Baptist",
    "matthew-17": "The Transfiguration of Jesus",
    "matthew-19": "What God Has Joined Together Let No Man Separate, The Rich Young Ruler, Let the Little Children Come to Me",
    "matthew-21": "The Triumphal Entry, Jesus Cleanses the Temple, Turning Over Tables",
    "matthew-25": "The Parable of the Ten Virgins, The Parable of the Talents, The Sheep and the Goats",
    "matthew-26": "The Last Supper, Jesus and the Disciples Eating Bread and Wine, This Is My Body This Is My Blood, Gethsemane, The Arrest of Jesus, Peter Denies Jesus Three Times",
    "matthew-27": "The Trial of Jesus, The Crucifixion, The Death of Jesus on the Cross",
    "matthew-28": "The Resurrection of Jesus from the Dead, Jesus Rose from the Grave on the Third Day, The Empty Tomb, The Stone Rolled Away, The Great Commission, Go and Make Disciples of All Nations",
    "mark-1": "The Baptism of Jesus, The Temptation in the Wilderness",
    "mark-4": "Jesus Calms the Storm, Parables of the Kingdom",
    "mark-5": "The Demoniac of the Gadarenes, Jairus's Daughter Raised",
    "mark-6": "Feeding the Five Thousand, Walking on Water",
    "mark-9": "The Transfiguration of Jesus",
    "mark-10": "What God Has Joined Together Let No Man Put Asunder, The Rich Young Man, Let the Children Come to Me",
    "mark-11": "The Triumphal Entry, Jesus Cleanses the Temple",
    "mark-12": "The Widow's Mite, The Widow's Two Small Coins, The Greatest Commandment",
    "mark-13": "The Olivet Discourse, Signs of the End of the Age, Wars and Rumors of Wars, The Great Tribulation, The Abomination of Desolation, The Son of Man Coming in the Clouds",
    "mark-14": "The Last Supper, Jesus Breaks Bread and Shares the Cup of Wine with His Disciples, Gethsemane, Peter Denies Jesus",
    "mark-15": "The Crucifixion of Jesus",
    "mark-16": "The Resurrection of Jesus from the Dead, Jesus Rose from the Grave, The Empty Tomb",
    "luke-1": "The Annunciation, The Birth of John the Baptist, Mary's Magnificat",
    "luke-2": "The Birth of Jesus in a Manger, The Shepherds, Simeon and Anna, Jesus in the Temple as a Boy",
    "luke-3": "The Baptism of Jesus",
    "luke-7": "The Centurion's Servant Healed, The Widow's Son Raised at Nain",
    "luke-8": "Jesus Calms the Storm",
    "luke-9": "The Transfiguration, Feeding the Five Thousand",
    "luke-10": "The Parable of the Good Samaritan, Mary and Martha",
    "luke-15": "The Parable of the Prodigal Son, The Father Who Welcomed Back His Lost Son, The Lost Sheep, The Lost Coin",
    "luke-16": "The Parable of the Rich Man and Lazarus",
    "luke-17": "The Ten Lepers Cleansed, If Your Brother Sins Rebuke Him and If He Repents Forgive Him, Forgiveness in the Bible",
    "luke-18": "The Parable of the Persistent Widow",
    "luke-19": "Zacchaeus Climbs the Sycamore Tree, The Triumphal Entry, Jesus Cleanses the Temple",
    "luke-21": "The Olivet Discourse, Signs of the End of the Age, The Destruction of Jerusalem, Nation Shall Rise Against Nation, The Great Tribulation, Jerusalem Trampled by Gentiles",
    "luke-22": "The Last Supper, The Bread and Wine of the New Covenant, Peter Denies Jesus",
    "luke-23": "The Crucifixion, The Thief on the Cross",
    "luke-24": "The Resurrection of Jesus from the Dead, The Road to Emmaus, The Ascension, Jesus Rose on the Third Day",
    "john-1": "In the Beginning Was the Word, The Logos, The Word Became Flesh and Dwelt Among Us, The Prologue of John's Gospel",
    "john-2": "The Wedding at Cana, Water Turned into Wine, Jesus Cleanses the Temple and Overturns the Money Changers' Tables",
    "john-3": "Nicodemus, You Must Be Born Again, For God So Loved the World, As Moses Lifted Up the Serpent in the Wilderness So Must the Son of Man Be Lifted Up",
    "john-4": "The Woman at the Well, The Samaritan Woman",
    "john-6": "Feeding the Five Thousand, Walking on Water, The Bread of Life",
    "john-8": "The Woman Caught in Adultery, Let He Who Is without Sin Cast the First Stone",
    "john-9": "Jesus Heals the Man Born Blind",
    "john-10": "The Good Shepherd",
    "john-11": "The Raising of Lazarus from the Dead",
    "john-13": "Jesus Washes the Disciples' Feet, The New Commandment",
    "john-14": "I Am the Way the Truth and the Life, The Comforter",
    "john-18": "The Arrest of Jesus, Peter Denies Jesus, Jesus Before Pilate",
    "john-19": "The Crucifixion of Jesus, It Is Finished",
    "john-20": "The Resurrection of Jesus from the Dead, Doubting Thomas, Jesus Rose from the Grave",
    "john-21": "The Miraculous Catch of Fish, Peter Restored",
    "acts-1": "The Ascension of Jesus",
    "acts-2": "The Day of Pentecost, Speaking in Tongues, Peter's Sermon",
    "acts-3": "Peter Heals the Lame Man at the Temple Gate",
    "acts-5": "Ananias and Sapphira",
    "acts-7": "The Stoning of Stephen, The First Christian Martyr",
    "acts-8": "Philip and the Ethiopian Eunuch",
    "acts-9": "The Conversion of Saul on the Road to Damascus",
    "acts-10": "Peter's Vision of the Sheet, Cornelius",
    "acts-12": "Peter's Miraculous Escape from Prison",
    "acts-16": "Paul and Silas in Prison, The Philippian Jailer",
    "acts-22": "Paul Recounts His Conversion on the Road to Damascus, Paul's Testimony Before the Crowd in Jerusalem",
    "acts-26": "Paul Recounts His Conversion on the Road to Damascus Before King Agrippa, A Light from Heaven Brighter Than the Sun",
    "acts-27": "Paul's Shipwreck on the Way to Rome",
    "revelation-4": "The Throne Room of Heaven",
    "revelation-6": "The Four Horsemen of the Apocalypse, The Opening of the Seven Seals, The Seal Judgments",
    "revelation-7": "The 144,000 Sealed",
    "revelation-12": "The Woman and the Dragon",
    "revelation-13": "The Beast, The Mark of the Beast, 666",
    "revelation-19": "The Marriage Supper of the Lamb, The Rider on the White Horse, The Battle of Armageddon, King of Kings and Lord of Lords",
    "revelation-20": "The Millennium, The Great White Throne Judgment",
    "revelation-21": "The New Heaven and New Earth, The New Jerusalem, The Streets of Gold, The Pearly Gates",
    "jude-1": "The Archangel Michael Contends with the Devil over the Body of Moses",
    # --- End Times / Prophecy ---
    "matthew-24": "The Olivet Discourse, Signs of the End Times, The Great Tribulation, Wars and Rumors of Wars, The Abomination of Desolation",
    "1_thessalonians-4": "The Rapture, The Dead in Christ Rise First, Caught Up in the Clouds to Meet the Lord",
    "1_thessalonians-5": "The Day of the Lord Comes as a Thief in the Night",
    "2_thessalonians-2": "The Man of Lawlessness, The Antichrist, The Great Falling Away, The Son of Perdition",
    "daniel-12": "The Time of the End, Michael Stands Up, Many Shall Run To and Fro and Knowledge Shall Increase",
    "joel-2": "The Day of the Lord, The Locust Army, The Sun Shall Be Turned to Darkness and the Moon to Blood, I Will Pour Out My Spirit Upon All Flesh",
    "zechariah-14": "The Lord Returns, His Feet Shall Stand on the Mount of Olives, Living Waters Flow from Jerusalem",
    "zechariah-12": "They Shall Look Upon Him Whom They Have Pierced, The Mourning of Jerusalem",
    "2_peter-3": "The Day of the Lord, The Heavens Shall Pass Away, The Elements Shall Melt with Fervent Heat, A New Heavens and New Earth",
    "revelation-1": "The Revelation of Jesus Christ, The Alpha and the Omega, The Beginning and the End, The Son of Man Among the Lampstands",
    "revelation-2": "Letters to the Seven Churches, Ephesus Smyrna Pergamum Thyatira",
    "revelation-3": "Letters to the Seven Churches, Sardis Philadelphia Laodicea, Behold I Stand at the Door and Knock",
    "revelation-5": "The Lamb Is Worthy to Open the Scroll, The Seven Seals, The Scroll with Seven Seals, The Seven Sealed Scroll",
    "revelation-8": "The Seven Trumpets, Hail and Fire Mixed with Blood",
    "revelation-9": "The Fifth and Sixth Trumpets, The Locusts from the Abyss",
    "revelation-11": "The Two Witnesses Who Prophesy in Sackcloth for 1260 Days, Fire Proceeds from Their Mouth, They Are Killed and Rise After Three and a Half Days, The Two Olive Trees and Two Lampstands, The Seventh Trumpet",
    "revelation-14": "The 144000 on Mount Zion, The Three Angels' Messages, The Harvest of the Earth, The Angel with the Everlasting Gospel",
    "revelation-16": "The Seven Bowls of God's Wrath, Armageddon",
    "revelation-17": "The Whore of Babylon, Mystery Babylon the Great, The Scarlet Beast",
    "revelation-18": "The Fall of Babylon, Babylon the Great Is Fallen, The Destruction of Babylon, The Whore of Babylon Judged, Come Out of Her My People",
    "revelation-22": "The River of Life, The Tree of Life, Come Lord Jesus, Maranatha",
    # --- Persecution / Encouragement / Spiritual Warfare ---
    "romans-8": "Nothing Can Separate Us from the Love of God, More Than Conquerors, All Things Work Together for Good",
    "ephesians-5": "Husbands Love Your Wives as Christ Loved the Church, Marriage as Christ and the Church, Walk as Children of Light",
    "ephesians-6": "The Full Armor of God, Put on the Whole Armor of God, The Belt of Truth, The Breastplate of Righteousness, The Shield of Faith, The Helmet of Salvation, The Sword of the Spirit, Spiritual Warfare, We Wrestle Not Against Flesh and Blood",
    "hebrews-12": "A Great Cloud of Witnesses, Run the Race with Endurance, God Disciplines Those He Loves",
    "james-1": "Count It All Joy When You Fall into Various Trials, The Testing of Your Faith",
    "1_peter-4": "Rejoice in Suffering, The Fiery Trial, Do Not Be Surprised at Persecution",
    "1_peter-5": "Cast All Your Cares Upon Him, Be Sober Be Vigilant, The Devil Prowls Like a Roaring Lion",
    "2_timothy-3": "Perilous Times Shall Come in the Last Days, All Scripture Is God-Breathed, Inspired by God",
    "2_timothy-4": "I Have Fought the Good Fight, I Have Finished the Race, I Have Kept the Faith",
    "1_peter-3": "Always Be Ready to Give a Reason for the Hope That Is in You",
    # --- Doctrine / Salvation / Teaching ---
    "romans-1": "The Wrath of God Revealed Against Unrighteousness, The Righteous Shall Live by Faith",
    "romans-3": "All Have Sinned and Fall Short of the Glory of God, Justified by Faith",
    "romans-5": "While We Were Yet Sinners Christ Died for Us, Justified by His Blood",
    "romans-10": "If You Confess with Your Mouth, Faith Comes by Hearing",
    "romans-12": "Present Your Bodies as a Living Sacrifice, Be Not Conformed to This World, Be Transformed by the Renewing of Your Mind",
    "1-corinthians-5": "Christ Our Passover Lamb Is Sacrificed, Purge Out the Old Leaven",
    "1-corinthians-13": "The Love Chapter, Love Is Patient Love Is Kind, The Greatest of These Is Love, Though I Speak with the Tongues of Angels",
    "1-corinthians-15": "The Resurrection of the Dead, Death Where Is Your Sting, The Last Trumpet, The Dead Shall Be Raised Incorruptible, What Happens After Death, The Bible on Death and the Afterlife",
    "2_corinthians-5": "If Any Man Be in Christ He Is a New Creation, Old Things Have Passed Away",
    "2_corinthians-10": "Pulling Down Strongholds, The Weapons of Our Warfare Are Not Carnal, Casting Down Imaginations",
    "2_corinthians-12": "My Grace Is Sufficient for Thee, Power Is Made Perfect in Weakness",
    "galatians-2": "I Am Crucified with Christ, It Is No Longer I Who Live But Christ Who Lives in Me",
    "philippians-4": "Be Anxious for Nothing, Do Not Worry, Rejoice in the Lord Always, The Peace of God, I Can Do All Things Through Christ",
    "philippians-2": "Every Knee Shall Bow and Every Tongue Shall Confess That Jesus Christ Is Lord, The Mind of Christ, He Humbled Himself and Became Obedient unto Death",
    "colossians-3": "Set Your Mind on Things Above, Put on the New Self, Bear with Each Other and Forgive One Another as the Lord Forgave You",
    "hebrews-4": "The Word of God Is Living and Active, Sharper Than Any Two-Edged Sword",
    "hebrews-9": "Without the Shedding of Blood There Is No Remission of Sin, No Forgiveness without Blood, The Blood Atonement Doctrine Explained, Christ Offered Once to Bear the Sins of Many",
    "hebrews-13": "Jesus Christ the Same Yesterday Today and Forever",
    "james-2": "Faith Without Works Is Dead",
    "1_john-1": "If We Confess Our Sins He Is Faithful and Just to Forgive, Walking in the Light, Fellowship with God and One Another",
    "1_john-3": "Children of God, Cain's Murder of Abel, Love One Another in Deed and Truth",
    "1_john-4": "God Is Love, Perfect Love Casts Out Fear, We Love Because He First Loved Us",
    "titus-3": "Saved Not by Works of Righteousness But by His Mercy",
    "john-15": "I Am the Vine You Are the Branches, Abide in Me, Greater Love Has No Man Than This",
    "john-17": "Jesus' High Priestly Prayer, That They May Be One",
    # --- More OT Events and Wisdom ---
    "genesis-14": "Melchizedek Blesses Abraham, The King of Salem, Priest of the Most High God",
    "genesis-24": "Isaac and Rebekah, Abraham's Servant Finds a Wife at the Well",
    "genesis-50": "Joseph Forgives His Brothers, You Meant It for Evil But God Meant It for Good",
    "exodus-15": "The Song of Moses and Miriam, The Song of the Sea",
    "exodus-25": "The Ark of the Covenant, The Tabernacle Instructions, The Mercy Seat",
    "numbers-6": "The Nazirite Vow, The Aaronic Blessing, The Lord Bless You and Keep You",
    "numbers-21": "The Bronze Serpent on the Pole, Look and Live",
    "deuteronomy-6": "The Shema, Hear O Israel the Lord Our God the Lord Is One",
    "deuteronomy-28": "Blessings and Curses, Blessings for Obedience Curses for Disobedience",
    "deuteronomy-30": "Choose Life or Death, I Set Before You Life and Death Blessing and Cursing",
    "deuteronomy-18": "A Prophet Like Moses, God Will Raise Up a Prophet from Among You",
    "1-samuel-8": "Israel Demands a King, Rejection of God as King",
    "1-samuel-15": "Saul Rejected as King, Obedience Is Better Than Sacrifice",
    "2-samuel-7": "God's Covenant with David, The Davidic Covenant, Your Throne Shall Be Established Forever",
    "1-kings-11": "Solomon's Downfall, Solomon's Wives Turn His Heart Away",
    "1-kings-21": "Naboth's Vineyard, Jezebel's Plot",
    "2chronicles-7": "If My People Who Are Called by My Name Shall Humble Themselves and Pray and Seek My Face and Turn from Their Wicked Ways Then Will I Hear from Heaven and Forgive Their Sin and Heal Their Land",
    "2chronicles-17": "Jehoshaphat Strengthens Judah, The LORD Was with Jehoshaphat Because He Followed the Ways of David",
    "2chronicles-20": "Jehoshaphat's Prayer Before Battle, The Battle Belongs to the Lord, We Do Not Know What to Do But Our Eyes Are on You",
    "2chronicles-19": "Jehoshaphat Appoints Judges, Reform of the Judiciary, Deal Faithfully with a Perfect Heart",
    "nehemiah-1": "Nehemiah's Prayer, Rebuilding the Walls of Jerusalem",
    "esther-4": "For Such a Time as This, Esther Approaches the King",
    "job-19": "I Know That My Redeemer Lives",
    "job-42": "Job's Restoration, The Lord Blessed the Latter End of Job",
    "psalms-1": "Blessed Is the Man Who Walks Not in the Counsel of the Ungodly",
    "psalms-22": "My God My God Why Have You Forsaken Me, A Messianic Psalm",
    "psalms-27": "The Lord Is My Light and My Salvation, Whom Shall I Fear",
    "psalms-34": "The Angel of the Lord Encamps Around Those Who Fear Him, Taste and See That the Lord Is Good",
    "psalms-37": "Delight Yourself in the Lord, Do Not Fret Because of Evildoers",
    "psalms-100": "Make a Joyful Noise Unto the Lord, Enter His Gates with Thanksgiving",
    "psalms-103": "Bless the Lord O My Soul, He Forgives All Your Iniquities",
    "psalms-121": "I Will Lift Up My Eyes to the Hills, My Help Comes from the Lord",
    "psalms-139": "You Knit Me Together in My Mother's Womb, Fearfully and Wonderfully Made",
    "psalms-150": "Let Everything That Has Breath Praise the Lord, Praise Him with Instruments",
    "proverbs-3": "Trust in the Lord with All Your Heart and Lean Not on Your Own Understanding, In All Your Ways Acknowledge Him and He Shall Direct Your Paths",
    "proverbs-31": "The Virtuous Woman, The Proverbs 31 Woman, Her Price Is Far Above Rubies",
    "isaiah-11": "The Root of Jesse, The Branch, The Wolf Shall Dwell with the Lamb, The Peaceable Kingdom",
    "isaiah-61": "The Spirit of the Lord Is Upon Me, Good News to the Poor, The Year of the Lord's Favor",
    "jeremiah-1": "The Call of Jeremiah, Before I Formed You in the Womb I Knew You",
    "jeremiah-31": "The New Covenant, I Will Write My Law on Their Hearts, I Will Be Their God",
    "lamentations-3": "Great Is Thy Faithfulness, The Faithfulness of God, His Mercies Are New Every Morning, The Lord's Compassions Never Fail",
    "hosea-6": "Come Let Us Return to the Lord, He Will Heal Us",
    "micah-6": "What Does the Lord Require of You, Act Justly Love Mercy Walk Humbly with Your God",
    "habakkuk-2": "The Just Shall Live by Faith, Write the Vision Make It Plain",
    "malachi-3": "Will a Man Rob God, Tithes and Offerings, Bring the Whole Tithe into the Storehouse, What Does the Bible Say About Tithing, Giving a Tenth to God, Return to Me and I Will Return to You",
    # --- More NT Events ---
    "numbers-15": "Laws of Offerings, Grain and Drink Offerings, The Sabbath-Breaker Stoned, Tassels on Garments",
    "numbers-18": "Duties of Priests and Levites, The Levitical Tithe, Offerings Given to Aaron and His Sons",
    "1-corinthians-7": "Marriage and Singleness, Paul's Teaching on Marriage and Celibacy, To the Unmarried and Widows",
    "leviticus-5": "The Guilt Offering, Sin Offerings for Unintentional Sin",
    "leviticus-25": "The Year of Jubilee, Sabbath Year for the Land, Redemption of Property",
    "exodus-31": "Bezalel and Oholiab Appointed as Craftsmen for the Tabernacle, The Sabbath Command",
    "2-samuel-15": "Absalom's Rebellion Against David, David Flees Jerusalem",
    "2-samuel-23": "David's Mighty Warriors, The Last Words of David, The Thirty Mighty Men",
    "2chronicles-34": "Josiah's Reforms, The Book of the Law Found in the Temple",
    "isaiah-22": "A Prophecy Against the Valley of Vision, Shebna the Steward Replaced by Eliakim",
    "isaiah-31": "Woe to Those Who Go Down to Egypt for Help, Trust in God Not in Horses and Chariots",
    "matthew-12": "A House Divided Against Itself Cannot Stand, The Unforgivable Sin, Blasphemy Against the Holy Spirit, The Sign of Jonah",
    "matthew-7": "Judge Not Lest You Be Judged, The Narrow Gate, Build Your House on the Rock",
    "matthew-18": "The Parable of the Unforgiving Servant, Forgiveness in the Bible, Forgive Seventy Times Seven, If Your Brother Sins Against You, Where Two or Three Are Gathered",
    "matthew-23": "Woe to the Scribes and Pharisees, You Tithe Mint Dill and Cumin But Neglect Justice Mercy and Faithfulness, What Does the Bible Say About Tithing",
    "matthew-22": "The Greatest Commandment, Love the Lord Your God, Love Your Neighbor as Yourself",
    "luke-4": "The Temptation of Jesus, Jesus Reads Isaiah in the Synagogue, The Spirit of the Lord Is Upon Me, Jesus Rejected at Nazareth",
    "acts-17": "Paul in Athens, The Unknown God, In Him We Live and Move and Have Our Being",
    "1_timothy-6": "The Love of Money Is the Root of All Evil, Fight the Good Fight of Faith",
    "2_timothy-2": "A Workman Who Does Not Need to Be Ashamed, Rightly Dividing the Word of Truth",
}


VERSE_ANCHORS = {
    # --- Core well-known verses ---
    "job-1": "The Lord gave and the Lord has taken away; blessed be the name of the Lord",
    "psalms-23": "The Lord is my shepherd I shall not want; yea though I walk through the valley of the shadow of death I will fear no evil for thou art with me; thy rod and thy staff they comfort me; he makes me lie down in green pastures he leads me beside still waters",
    "proverbs-22": "Train up a child in the way he should go and when he is old he will not depart from it",
    "proverbs-9": "The fear of the Lord is the beginning of wisdom",
    "ecclesiastes-3": "To everything there is a season; a time to be born and a time to die",
    "matthew-4": "Man shall not live by bread alone but by every word that proceeds from the mouth of God",
    "matthew-5": "Blessed are the poor in spirit for theirs is the kingdom of heaven; the Beatitudes",
    "matthew-11": "Come unto me all ye that labor and are heavy laden and I will give you rest",
    "john-1": "In the beginning was the Word and the Word was with God and the Word was God; the Word became flesh and dwelt among us; the Gospel of John chapter one; the Logos",
    "john-3": "For God so loved the world that he gave his only begotten Son",
    "john-14": "I am the way the truth and the life; no man comes to the Father but by me",
    "1-corinthians-13": "Love is patient love is kind; love bears all things; the greatest of these is love",
    "galatians-5": "The fruit of the spirit is love joy peace patience kindness goodness faithfulness gentleness self-control",
    "ephesians-2": "By grace you have been saved through faith; it is the gift of God not of works",
    "philippians-4": "Be careful for nothing but in every thing by prayer and supplication with thanksgiving let your requests be made known unto God; and the peace of God which passeth all understanding shall keep your hearts and minds; be anxious for nothing; do not worry about anything; I can do all things through Christ who strengthens me",
    "hebrews-11": "Faith is the substance of things hoped for the evidence of things not seen; by faith Abel offered a more excellent sacrifice than Cain; by faith Noah built the ark; by faith Abraham; by faith Moses; by faith the walls of Jericho fell; by faith Rahab",
    # Resurrection anchors
    "mark-16": "The resurrection of Jesus Christ on the third day; the stone was rolled away; the young man said He is risen He is not here; Jesus appeared first to Mary Magdalene after He rose from the dead",
    "luke-24": "The resurrection of Jesus Christ on the third day; He is not here but is risen; the women found the tomb empty; the road to Emmaus; Jesus ate with the disciples after rising from the dead",
    "john-20": "The resurrection of Jesus Christ on the third day; Mary Magdalene found the stone rolled away from the empty tomb; Jesus appeared to the disciples; Thomas touched His wounds and said my Lord and my God",
    # Transfiguration anchors
    "matthew-17": "The transfiguration of Jesus on the mountain; his face shone like the sun; Moses and Elijah appeared; a bright cloud overshadowed them",
    "mark-9": "The transfiguration of Jesus; his clothes became dazzling white; Moses and Elijah appeared talking with him on the high mountain",
    "luke-9": "The transfiguration of Jesus on the mountain; his face changed and his clothes became bright; Moses and Elijah appeared in glory",
    # Ten commandments
    "deuteronomy-5": "The ten commandments restated by Moses: thou shalt have no other gods; no graven images; do not take God's name in vain; keep the Sabbath; honor thy father and mother; thou shalt not kill; thou shalt not commit adultery; thou shalt not steal; thou shalt not bear false witness; thou shalt not covet",
    # Other concept anchors
    "joshua-10": "The sun stood still; Joshua commanded the sun to stop in the sky; the longest day; the sun standing still over Gibeon",
    "1-kings-3": "Solomon asked God for wisdom; God appeared to Solomon in a dream; ask what I shall give thee; Solomon chose wisdom over riches and long life",
    "jude-1": "The archangel Michael contended with the devil and disputed about the body of Moses; Satan fighting over Moses' body",
    "luke-15": "The prodigal son; the younger son wasted his inheritance in reckless living; the father ran to welcome back his lost son; the father embraced the prodigal son who returned home; the lost sheep; the lost coin",
    "psalms-137": "By the rivers of Babylon there we sat down yea we wept when we remembered Zion",
    # --- End Times / Prophecy ---
    "matthew-24": "Nation shall rise against nation; there shall be famines and earthquakes; he that endures to the end shall be saved; as lightning comes from the east so shall the coming of the Son of Man be; when you see the abomination of desolation spoken of by Daniel",
    "1_thessalonians-4": "The Lord himself shall descend from heaven with a shout; the dead in Christ shall rise first; then we which are alive and remain shall be caught up together with them in the clouds to meet the Lord in the air",
    "1_thessalonians-5": "The day of the Lord so comes as a thief in the night; when they shall say peace and safety then sudden destruction comes",
    "2_thessalonians-2": "That day shall not come except there come a falling away first and that man of sin be revealed the son of perdition; who opposes and exalts himself above all that is called God",
    "daniel-12": "At that time Michael shall stand up; many of them that sleep in the dust of the earth shall awake; they that be wise shall shine as the brightness of the firmament",
    "joel-2": "The sun shall be turned into darkness and the moon into blood before the great and terrible day of the Lord come; I will pour out my spirit upon all flesh",
    "zechariah-14": "His feet shall stand in that day upon the mount of Olives; living waters shall go out from Jerusalem; the Lord shall be king over all the earth",
    "zechariah-12": "They shall look upon me whom they have pierced and they shall mourn for him as one mourns for his only son",
    "2_peter-3": "The day of the Lord will come as a thief in the night; the heavens shall pass away with a great noise; the elements shall melt with fervent heat; we look for new heavens and a new earth",
    "revelation-1": "I am Alpha and Omega the beginning and the ending; behold he comes with clouds and every eye shall see him",
    "revelation-3": "Behold I stand at the door and knock; if any man hear my voice and open the door I will come in to him",
    "revelation-5": "Worthy is the Lamb that was slain to receive power and riches and wisdom and strength and honor and glory and blessing",
    "revelation-6": "I looked and behold a pale horse and his name that sat on him was Death; the four horsemen",
    "revelation-7": "I heard the number of them which were sealed; a hundred and forty-four thousand of all the tribes of the children of Israel",
    "revelation-11": "I will give power unto my two witnesses and they shall prophesy a thousand two hundred and threescore days clothed in sackcloth; these are the two olive trees and the two candlesticks; fire proceeds out of their mouth; they are killed and after three and a half days the spirit of life enters them and they stand upon their feet",
    "revelation-13": "He causes all both small and great to receive a mark in their right hand or in their foreheads; the number of the beast is six hundred threescore and six; 666",
    "revelation-16": "He gathered them together into a place called in the Hebrew tongue Armageddon; the seven bowls of the wrath of God",
    "revelation-17": "Mystery Babylon the Great the Mother of Harlots and Abominations of the Earth; the woman was arrayed in purple and scarlet",
    "revelation-18": "Babylon the great is fallen is fallen; come out of her my people that you be not partakers of her sins",
    "revelation-19": "Blessed are they which are called unto the marriage supper of the Lamb; the armies which were in heaven followed him on white horses; King of kings and Lord of lords; the battle of Armageddon; the beast and the false prophet cast into the lake of fire",
    "revelation-20": "I saw thrones and they sat upon them and judgment was given unto them; they lived and reigned with Christ a thousand years; the millennium; the great white throne judgment",
    "revelation-21": "I saw a new heaven and a new earth; God shall wipe away all tears from their eyes; there shall be no more death neither sorrow nor crying; the street of the city was pure gold; the twelve gates were twelve pearls; streets of gold and pearly gates",
    "revelation-22": "The Spirit and the bride say Come; behold I come quickly; even so come Lord Jesus; the river of the water of life; the tree of life",
    # --- Persecution / Encouragement / Spiritual Warfare ---
    "romans-8": "All things work together for good to them that love God; who shall separate us from the love of Christ; we are more than conquerors through him that loved us; neither death nor life nor angels nor principalities shall be able to separate us from the love of God; if God be for us who can be against us",
    "ephesians-6": "Put on the whole armor of God; the belt of truth; the breastplate of righteousness; the shield of faith; the helmet of salvation; the sword of the Spirit which is the word of God; we wrestle not against flesh and blood",
    "hebrews-12": "Seeing we are compassed about with so great a cloud of witnesses; let us run with patience the race that is set before us; whom the Lord loves he chastens",
    "james-1": "Count it all joy when you fall into divers temptations; the trying of your faith works patience; blessed is the man that endures temptation; every good gift and every perfect gift is from above",
    "1_peter-4": "Beloved think it not strange concerning the fiery trial which is to try you; rejoice inasmuch as you are partakers of Christ's sufferings",
    "1_peter-5": "Casting all your care upon him for he cares for you; be sober be vigilant because your adversary the devil as a roaring lion walks about seeking whom he may devour",
    "2_timothy-3": "In the last days perilous times shall come; men shall be lovers of their own selves; all scripture is given by inspiration of God and is profitable for doctrine",
    "2_timothy-4": "I have fought a good fight I have finished my course I have kept the faith; henceforth there is laid up for me a crown of righteousness",
    "1_peter-3": "Be ready always to give an answer to every man that asks you a reason of the hope that is in you with meekness and fear",
    "psalms-91": "He that dwells in the secret place of the most High shall abide under the shadow of the Almighty; a thousand shall fall at your side and ten thousand at your right hand but it shall not come near you",
    "psalms-46": "God is our refuge and strength a very present help in trouble; be still and know that I am God",
    "psalms-27": "The Lord is my light and my salvation whom shall I fear; the Lord is the strength of my life of whom shall I be afraid",
    "psalms-121": "I will lift up mine eyes unto the hills from whence comes my help; my help comes from the Lord which made heaven and earth",
    "psalms-34": "The angel of the Lord encamps round about them that fear him and delivers them; O taste and see that the Lord is good; the righteous cry and the Lord hears",
    # --- Doctrine / Salvation / Teaching ---
    "romans-1": "The just shall live by faith; the wrath of God is revealed from heaven against all ungodliness; I am not ashamed of the gospel of Christ",
    "romans-3": "All have sinned and come short of the glory of God; being justified freely by his grace through the redemption that is in Christ Jesus",
    "romans-5": "While we were yet sinners Christ died for us; being justified by faith we have peace with God; by one man sin entered into the world and death by sin",
    "romans-6": "The wages of sin is death but the gift of God is eternal life through Jesus Christ our Lord",
    "romans-10": "If thou shalt confess with thy mouth the Lord Jesus and shalt believe in thine heart that God hath raised him from the dead thou shalt be saved; faith comes by hearing and hearing by the word of God",
    "romans-12": "Present your bodies a living sacrifice holy acceptable unto God; be not conformed to this world but be ye transformed by the renewing of your mind",
    "1-corinthians-15": "O death where is thy sting; O grave where is thy victory; the last trumpet shall sound and the dead shall be raised incorruptible; if Christ be not risen then is our preaching vain",
    "2_corinthians-5": "If any man be in Christ he is a new creature; old things are passed away behold all things are become new",
    "2_corinthians-12": "My grace is sufficient for thee for my strength is made perfect in weakness; when I am weak then am I strong",
    "galatians-2": "I am crucified with Christ nevertheless I live yet not I but Christ lives in me; the life which I now live in the flesh I live by the faith of the Son of God",
    "philippians-2": "At the name of Jesus every knee should bow; every tongue should confess that Jesus Christ is Lord; he humbled himself and became obedient unto death even the death of the cross",
    "colossians-3": "Set your affection on things above not on things on the earth; put on the new man which is renewed in knowledge",
    "hebrews-4": "The word of God is quick and powerful and sharper than any twoedged sword; let us come boldly unto the throne of grace",
    "hebrews-9": "Without shedding of blood is no remission; Christ was once offered to bear the sins of many",
    "hebrews-13": "Jesus Christ the same yesterday and today and forever; let brotherly love continue",
    "james-2": "Faith without works is dead; show me your faith without your works and I will show you my faith by my works",
    "1_john-1": "If we confess our sins he is faithful and just to forgive us our sins and to cleanse us from all unrighteousness; the blood of Jesus Christ his Son cleanses us from all sin",
    "1_john-4": "God is love; perfect love casts out fear; we love him because he first loved us; beloved let us love one another for love is of God",
    "titus-3": "Not by works of righteousness which we have done but according to his mercy he saved us by the washing of regeneration",
    "john-15": "I am the vine ye are the branches; abide in me and I in you; greater love hath no man than this that a man lay down his life for his friends",
    "john-17": "That they all may be one as thou Father art in me and I in thee; sanctify them through thy truth thy word is truth",
    "john-8": "You shall know the truth and the truth shall make you free; let him who is without sin cast the first stone",
    # --- More OT Wisdom / Events ---
    "genesis-1": "In the beginning God created the heaven and the earth; and God said let there be light and there was light",
    "genesis-3": "The serpent beguiled me and I did eat; unto the woman he said I will greatly multiply thy sorrow; the seed of the woman shall bruise the serpent's head; dust thou art and unto dust shalt thou return; the talking snake in the garden; the book where the snake talks to Eve; Genesis has the story of the serpent who spoke",
    "genesis-50": "You meant evil against me but God meant it for good to save many people alive",
    "exodus-3": "I AM THAT I AM; put off thy shoes from off thy feet for the place whereon thou standest is holy ground; the burning bush",
    "exodus-14": "Fear ye not stand still and see the salvation of the Lord; the Lord shall fight for you and ye shall hold your peace; the parting of the Red Sea",
    "numbers-6": "The Lord bless thee and keep thee; the Lord make his face shine upon thee and be gracious unto thee; the Lord lift up his countenance upon thee and give thee peace",
    "numbers-21": "Make thee a fiery serpent and set it upon a pole; everyone that is bitten when he looks upon it shall live; the bronze serpent",
    "deuteronomy-6": "Hear O Israel the Lord our God is one Lord; thou shalt love the Lord thy God with all thine heart and with all thy soul and with all thy might; the Shema",
    "deuteronomy-28": "Blessed shalt thou be in the city and blessed shalt thou be in the field; cursed shalt thou be if thou wilt not hearken unto the voice of the Lord thy God",
    "deuteronomy-30": "I have set before you life and death blessing and cursing; therefore choose life that both thou and thy seed may live",
    "deuteronomy-18": "The Lord thy God will raise up unto thee a Prophet from the midst of thee like unto me; unto him ye shall hearken",
    "1-samuel-15": "To obey is better than sacrifice and to hearken than the fat of rams; rebellion is as the sin of witchcraft",
    "2-samuel-7": "Thy throne shall be established forever; I will be his father and he shall be my son; the Davidic covenant",
    "2chronicles-7": "If my people which are called by my name shall humble themselves and pray and seek my face and turn from their wicked ways then will I hear from heaven and will forgive their sin and will heal their land",
    "esther-4": "Who knows whether you have come to the kingdom for such a time as this",
    "job-19": "I know that my redeemer lives and that he shall stand at the latter day upon the earth",
    "job-42": "I have heard of thee by the hearing of the ear but now mine eye sees thee; the Lord blessed the latter end of Job more than his beginning",
    "psalms-1": "Blessed is the man that walks not in the counsel of the ungodly; he shall be like a tree planted by the rivers of water",
    "psalms-22": "My God my God why hast thou forsaken me; they pierced my hands and my feet; they parted my garments among them and cast lots upon my vesture",
    "psalms-37": "Delight thyself also in the Lord and he shall give thee the desires of thine heart; commit thy way unto the Lord; fret not thyself because of evildoers",
    "psalms-51": "Create in me a clean heart O God and renew a right spirit within me; have mercy upon me O God according to thy lovingkindness",
    "psalms-100": "Make a joyful noise unto the Lord all ye lands; enter into his gates with thanksgiving and into his courts with praise",
    "psalms-103": "Bless the Lord O my soul and all that is within me bless his holy name; who forgives all your iniquities who heals all your diseases",
    "psalms-119": "Thy word is a lamp unto my feet and a light unto my path; thy word have I hid in mine heart that I might not sin against thee",
    "psalms-139": "I will praise thee for I am fearfully and wonderfully made; thou hast searched me and known me; you knit me together in my mother's womb",
    "psalms-150": "Let every thing that hath breath praise the Lord; praise him with the sound of the trumpet; praise him with the psaltery and harp",
    "proverbs-3": "Trust in the Lord with all thine heart and lean not unto thine own understanding; in all thy ways acknowledge him and he shall direct thy paths",
    "proverbs-31": "Who can find a virtuous woman for her price is far above rubies; strength and honor are her clothing",
    "isaiah-7": "Behold a virgin shall conceive and bear a son and shall call his name Immanuel",
    "isaiah-9": "For unto us a child is born unto us a son is given and the government shall be upon his shoulder",
    "isaiah-11": "The wolf also shall dwell with the lamb; a rod out of the stem of Jesse; the spirit of the Lord shall rest upon him",
    "isaiah-14": "How art thou fallen from heaven O Lucifer son of the morning; I will ascend above the heights of the clouds I will be like the most High",
    "isaiah-40": "Comfort ye comfort ye my people; they that wait upon the Lord shall renew their strength; they shall mount up with wings as eagles",
    "isaiah-41": "Fear thou not for I am with thee; be not dismayed for I am thy God; I will strengthen thee yea I will help thee; I will uphold thee with the right hand of my righteousness; do not be afraid",
    "isaiah-53": "He was wounded for our transgressions he was bruised for our iniquities; the chastisement of our peace was upon him and with his stripes we are healed; he was despised and rejected of men a man of sorrows",
    "isaiah-61": "The Spirit of the Lord God is upon me because he has anointed me to preach good tidings unto the meek; to proclaim the acceptable year of the Lord",
    "jeremiah-1": "Before I formed thee in the belly I knew thee; before thou camest forth out of the womb I sanctified thee",
    "jeremiah-29": "I know the thoughts that I think toward you says the Lord thoughts of peace and not of evil to give you an expected end; for I know the plans I have for you",
    "jeremiah-31": "I will put my law in their inward parts and write it in their hearts; I will be their God and they shall be my people; the new covenant",
    "lamentations-3": "Great is thy faithfulness; the faithfulness of God; his compassions fail not; they are new every morning; it is of the Lord's mercies that we are not consumed; great is thy faithfulness O Lord",
    "hosea-6": "Come and let us return unto the Lord for he hath torn and he will heal us; after two days will he revive us in the third day he will raise us up",
    "micah-6": "What doth the Lord require of thee but to do justly and to love mercy and to walk humbly with thy God",
    "habakkuk-2": "The just shall live by his faith; write the vision and make it plain upon tables that he may run that reads it",
    "malachi-3": "Will a man rob God; bring ye all the tithes into the storehouse that there may be food in my house; test me in this says the Lord Almighty and see if I will not throw open the floodgates of heaven and pour out so much blessing; what does the Bible say about tithing; giving a tenth of your income to God; return unto me and I will return unto you",
    # --- More NT ---
    "matthew-7": "Judge not that ye be not judged; enter ye in at the strait gate; ask and it shall be given you seek and ye shall find; build your house upon the rock",
    "matthew-22": "Thou shalt love the Lord thy God with all thy heart and with all thy soul and with all thy mind; this is the first and great commandment; the second is like unto it thou shalt love thy neighbor as thyself",
    "matthew-25": "I was hungry and you gave me food; inasmuch as you have done it unto one of the least of these my brethren you have done it unto me; the parable of the ten virgins; the talents",
    "matthew-23": "Woe to you scribes and Pharisees hypocrites; you give a tenth of your spices mint dill and cumin but you have neglected the more important matters of the law justice mercy and faithfulness; you should have practiced the latter without neglecting the former; tithing in the Bible",
    "matthew-26": "He took bread and gave thanks and broke it and gave it to his disciples saying take eat this is my body; he took the cup saying drink from it all of you this is my blood of the covenant; the Last Supper; the bread and the wine; the night Jesus was betrayed",
    "matthew-28": "He is not here for he is risen as he said; come see the place where the Lord lay; Jesus rose from the dead on the third day; the resurrection of Jesus; go ye therefore and teach all nations; lo I am with you always even unto the end of the world",
    "luke-4": "The Spirit of the Lord is upon me because he has anointed me to preach the gospel to the poor; man shall not live by bread alone but by every word of God",
    "acts-2": "They were all filled with the Holy Ghost and began to speak with other tongues; repent and be baptized every one of you; this is that which was spoken by the prophet Joel",
    "acts-17": "In him we live and move and have our being; God that made the world and all things therein; the unknown God",
    "1_timothy-6": "The love of money is the root of all evil; fight the good fight of faith lay hold on eternal life",
    "2_timothy-2": "Study to show thyself approved unto God a workman that needeth not to be ashamed rightly dividing the word of truth",
    # --- Core stories needing verse anchors ---
    "genesis-22": "God will provide himself a lamb for a burnt offering; take now thy son thine only son Isaac whom thou lovest; because thou hast not withheld thy son thine only son",
    "exodus-12": "When I see the blood I will pass over you; the lamb without blemish; this day shall be unto you for a memorial; the Passover",
    "exodus-20": "Thou shalt have no other gods before me; thou shalt not kill; thou shalt not steal; thou shalt not bear false witness; thou shalt not covet; honor thy father and thy mother; remember the sabbath day to keep it holy",
    "1-kings-18": "Then the fire of the Lord fell and consumed the burnt sacrifice and the wood and the stones and the dust and licked up the water that was in the trench; Elijah called down fire from heaven on the altar at Mount Carmel; the prophets of Baal cried out from morning to evening but there was no answer; how long will you waver between two opinions",
    "daniel-4": "The king's reason returned unto him; he was driven from men and did eat grass as oxen; his body was wet with the dew of heaven till his hairs were grown like eagles' feathers; Nebuchadnezzar's madness; seven times shall pass over thee; the Most High ruleth in the kingdom of men",
    "daniel-6": "My God hath sent his angel and hath shut the lions' mouths; Daniel in the lions' den",
    "daniel-3": "Our God whom we serve is able to deliver us from the burning fiery furnace; Shadrach Meshach and Abednego; the fourth man in the fire",
    "jonah-2": "Out of the belly of hell cried I and thou heardest my voice; salvation is of the Lord; Jonah's prayer from the belly of the fish",
    "ezekiel-37": "Can these bones live; O ye dry bones hear the word of the Lord; I will put my spirit in you and ye shall live; the valley of dry bones",
    "leviticus-23": "These are the feasts of the Lord even holy convocations; the Passover; unleavened bread; firstfruits; Pentecost; trumpets; day of atonement; tabernacles",
    "matthew-6": "Our Father which art in heaven hallowed be thy name; thy kingdom come thy will be done; give us this day our daily bread; the Lord's Prayer; take no thought for your life what ye shall eat; seek ye first the kingdom of God",
    "john-10": "I am the good shepherd; the good shepherd gives his life for the sheep; I am come that they might have life and that they might have it more abundantly",
    "john-11": "I am the resurrection and the life; he that believes in me though he were dead yet shall he live; Jesus wept; Lazarus come forth",
    "matthew-2": "When Jesus was born in Bethlehem; wise men from the east came to Jerusalem saying where is he that is born King of the Jews; the star; Herod sought the young child to destroy him; the birth of Jesus Christ; the flight into Egypt",
    "acts-9": "Saul Saul why persecutest thou me; who art thou Lord; I am Jesus whom thou persecutest; the conversion of Saul on the road to Damascus",
    "acts-22": "I am a Jew born in Tarsus; as I journeyed to Damascus a great light from heaven shone round about me; Saul Saul why persecutest thou me; Paul recounts his conversion on the road to Damascus",
    "acts-26": "At midday I saw a light from heaven brighter than the sun; I heard a voice saying Saul Saul why persecutest thou me; Paul tells King Agrippa about his conversion on the road to Damascus",
    "mark-13": "Nation shall rise against nation; there shall be earthquakes and famines; when ye shall see the abomination of desolation; the sun shall be darkened and the moon shall not give her light; they shall see the Son of man coming in the clouds; the Olivet Discourse",
    "luke-21": "Nation shall rise against nation and kingdom against kingdom; great earthquakes and famines and pestilences; Jerusalem shall be trodden down of the Gentiles; signs in the sun and in the moon and in the stars; then shall they see the Son of man coming in a cloud; the Olivet Discourse; signs of the end of the age",
    "psalms-106": "They made a calf in Horeb and worshipped the molten image; he rebuked the Red Sea also and it was dried up; they soon forgot his works; Israel's rebellion and unfaithfulness recalled",
    "exodus-21": "Eye for eye tooth for tooth hand for hand foot for foot; if a man smite his servant; the Hebrew laws of personal injury and justice",
    "matthew-12": "Every kingdom divided against itself is brought to desolation; a house divided against itself shall not stand; the blasphemy against the Holy Ghost shall not be forgiven; an evil and adulterous generation seeketh after a sign; the sign of the prophet Jonas",
    "1-samuel-28": "Saul said bring me up Samuel; the woman of Endor saw gods ascending out of the earth; the witch of Endor; Saul disguised himself and consulted a medium",
    "judges-3": "Ehud the son of Gera a Benjamite a man lefthanded; he thrust the dagger into the king's belly; Ehud the left-handed judge",
    "genesis-5": "All the days of Methuselah were nine hundred sixty and nine years and he died; the oldest man in the Bible; Seth Enosh Kenan Mahalalel Jared Enoch Methuselah Lamech Noah",
    "2_corinthians-10": "The weapons of our warfare are not carnal but mighty through God to the pulling down of strongholds; casting down imaginations; bringing into captivity every thought",
    "1-corinthians-5": "Christ our passover is sacrificed for us; purge out therefore the old leaven; the Passover lamb",
    "ephesians-5": "Husbands love your wives even as Christ also loved the church and gave himself for it; for this cause shall a man leave his father and mother and shall be joined unto his wife; marriage",
    "1_john-3": "Behold what manner of love the Father hath bestowed upon us that we should be called the sons of God; not as Cain who was of that wicked one and slew his brother",
    "isaiah-45": "I am the Lord and there is none else; unto me every knee shall bow every tongue shall swear; look unto me and be ye saved all the ends of the earth; I form the light and create darkness",
    "matthew-18": "If your brother or sister sins go and point out their fault; if they listen to you you have won them over; Lord how many times shall I forgive; not seven times but seventy-seven times; the parable of the unforgiving servant; forgiveness in the Bible; where two or three are gathered together in my name there am I in the midst of them",
    "matthew-19": "What therefore God hath joined together let not man put asunder; from the beginning he made them male and female; for this cause shall a man leave father and mother; suffer little children to come unto me; it is easier for a camel to go through the eye of a needle than for a rich man to enter into the kingdom of God",
    "mark-10": "What therefore God hath joined together let not man put asunder; from the beginning of the creation God made them male and female; suffer the little children to come unto me; with God all things are possible",
    "mark-12": "This poor widow hath cast more in than all they which have cast into the treasury; she of her want did cast in all that she had even all her living; the widow's mite; the widow's two small coins",
    "mark-14": "He took bread and when he had given thanks he broke it and gave it to them saying take it this is my body; he took the cup saying this is my blood of the covenant which is poured out for many; the Last Supper",
    "luke-17": "If your brother or sister sins against you rebuke them and if they repent forgive them; even if they sin against you seven times in a day and seven times come back to you saying I repent you must forgive them; forgiveness in the Bible",
    "luke-22": "He took bread gave thanks and broke it and gave it to them saying this is my body given for you; this cup is the new covenant in my blood which is poured out for you; the Last Supper; the bread and the wine",
    "2chronicles-19": "Jehoshaphat set judges in the land; deal courageously and the Lord shall be with the good; appointing judges and reforming the judiciary",
    "joshua-1": "Be strong and of a good courage; be not afraid neither be thou dismayed for the Lord thy God is with thee whithersoever thou goest; have not I commanded thee",
}

EVENT_CLUSTERS = {
    "baptism_of_jesus": {
        "stories": ["matthew-3", "mark-1", "luke-3"],
        "shared_anchor": "The baptism of Jesus by John in the Jordan River; the heavens opened; the Spirit descended like a dove; a voice from heaven said This is my beloved Son",
    },
    "transfiguration": {
        "stories": ["matthew-17", "mark-9", "luke-9"],
        "shared_anchor": "The transfiguration of Jesus on the mountain; his face shone like the sun; Moses and Elijah appeared; a bright cloud overshadowed them; a voice said This is my beloved Son",
    },
    "feeding_five_thousand": {
        "stories": ["matthew-14", "mark-6", "luke-9", "john-6"],
        "shared_anchor": "Jesus feeds five thousand men with five loaves and two fish; twelve baskets of fragments remained",
    },
    "crucifixion": {
        "stories": ["matthew-27", "mark-15", "luke-23", "john-19"],
        "shared_anchor": "The crucifixion of Jesus at Golgotha; darkness over the land; the veil of the temple torn; Jesus yielded up his spirit",
    },
    "resurrection": {
        "stories": ["matthew-28", "mark-16", "luke-24", "john-20"],
        "shared_anchor": "The resurrection of Jesus; the empty tomb; the stone rolled away; the angel said He is not here He is risen",
    },
    "great_commission": {
        "stories": ["matthew-28", "mark-16", "acts-1"],
        "shared_anchor": "The Great Commission; go and make disciples of all nations; baptizing them; teaching them; you shall be my witnesses to the ends of the earth",
    },
    "last_supper": {
        "stories": ["matthew-26", "mark-14", "luke-22", "john-13"],
        "shared_anchor": "The Last Supper; Jesus broke bread and said this is my body; he took the cup and said this is my blood of the new covenant",
    },
    "temptation_of_jesus": {
        "stories": ["matthew-4", "mark-1", "luke-4"],
        "shared_anchor": "The temptation of Jesus in the wilderness for forty days; the devil tempted him with bread, power, and the pinnacle of the temple",
    },
    "sermon_on_mount_plain": {
        "stories": ["matthew-5", "matthew-6", "matthew-7", "luke-6"],
        "shared_anchor": "The Sermon on the Mount; the Beatitudes; blessed are the poor in spirit; love your enemies; do not worry; the Lord's Prayer",
    },
    "passover_lamb_typology": {
        "stories": ["exodus-12", "john-19", "1-corinthians-5"],
        "shared_anchor": "The Passover lamb; Christ our Passover is sacrificed for us; the lamb without blemish; not a bone shall be broken",
    },
    "creation": {
        "stories": ["genesis-1", "genesis-2"],
        "shared_anchor": "God created the heavens and the earth; let there be light; God made man from the dust; male and female he created them; the garden of Eden",
    },
    "forgiveness": {
        "stories": ["matthew-18", "colossians-3", "ephesians-4"],
        "shared_anchor": "Forgiveness; forgive seventy times seven; forgiving one another as God in Christ forgave you; be kind and tenderhearted",
    },
    "marriage": {
        "stories": ["genesis-2", "ephesians-5", "1-corinthians-7"],
        "shared_anchor": "Marriage; a man shall leave his father and mother and cleave to his wife; the two shall become one flesh; husbands love your wives as Christ loved the church; what God has joined together let no man separate",
    },
    "blood_atonement": {
        "stories": ["leviticus-17", "hebrews-9", "hebrews-10"],
        "shared_anchor": "The blood atonement; without the shedding of blood there is no remission of sins; the blood of Christ; the life of the flesh is in the blood; the new covenant in his blood",
    },
    "death_afterlife": {
        "stories": ["1-corinthians-15", "1_thessalonians-4", "hebrews-9", "revelation-20"],
        "shared_anchor": "Death and what happens after; the resurrection of the dead; absent from the body present with the Lord; the dead in Christ shall rise first; the last judgment; the second death",
    },
    "healing_miracles": {
        "stories": ["acts-3", "mark-2", "john-5", "luke-5"],
        "shared_anchor": "Healing miracles of Jesus; rise take up your bed and walk; Peter heals the lame man; the paralytic lowered through the roof; the pool of Bethesda; your sins are forgiven",
    },
    "feeding_miracles": {
        "stories": ["matthew-14", "mark-6", "luke-9", "john-6", "matthew-15"],
        "shared_anchor": "Jesus feeds the multitude with loaves and fishes; five thousand fed with five loaves and two fish; four thousand fed; twelve baskets remained; the bread of life",
    },
    "fear_not": {
        "stories": ["isaiah-41", "isaiah-43", "joshua-1", "deuteronomy-31"],
        "shared_anchor": "Fear not for I am with you; be strong and courageous; do not be afraid; do not be dismayed; I will never leave you nor forsake you",
    },
    "suffering_theodicy": {
        "stories": ["job-1", "job-2", "job-38", "james-5"],
        "shared_anchor": "The suffering of the righteous; why do the righteous suffer; Job lost everything; the Lord gave and the Lord has taken away; the patience of Job; God's answer from the whirlwind",
    },
    "water_baptism_spirit": {
        "stories": ["acts-2", "acts-8", "romans-6", "acts-19"],
        "shared_anchor": "Baptism in water and in the Holy Spirit; buried with Christ in baptism; the gift of the Holy Spirit; Philip baptized the Ethiopian; tongues of fire at Pentecost; baptized into his death",
    },
    "lords_supper_communion": {
        "stories": ["matthew-26", "mark-14", "luke-22", "1-corinthians-11"],
        "shared_anchor": "The Lord's Supper; holy communion; this is my body broken for you; this cup is the new covenant in my blood; do this in remembrance of me; the bread and the wine",
    },
    "walking_on_water": {
        "stories": ["matthew-14", "mark-6", "john-6"],
        "shared_anchor": "Jesus walks on the water; the disciples saw him walking on the sea; Peter walked on the water; be not afraid it is I; the wind ceased",
    },
    "predestination_calling": {
        "stories": ["romans-8", "ephesians-1", "romans-9"],
        "shared_anchor": "Predestination and the calling of God; whom he foreknew he also predestined; chosen in Christ before the foundation of the world; called according to his purpose; election and sovereignty of God",
    },
    "calming_the_storm": {
        "stories": ["matthew-8", "mark-4", "luke-8"],
        "shared_anchor": "Jesus calms the storm; peace be still; the wind and the sea obey him; the disciples were afraid; why are you so fearful; O you of little faith",
    },
    "saul_conversion": {
        "stories": ["acts-9", "acts-22", "acts-26"],
        "shared_anchor": "The conversion of Saul on the road to Damascus; a light from heaven; Saul Saul why do you persecute me; he fell to the ground; Ananias laid hands on him; scales fell from his eyes",
    },
    "suffering_servant": {
        "stories": ["isaiah-52", "isaiah-53"],
        "shared_anchor": "The suffering servant; he was despised and rejected; a man of sorrows acquainted with grief; wounded for our transgressions; bruised for our iniquities; by his stripes we are healed; led as a lamb to the slaughter",
    },
    "birth_of_jesus": {
        "stories": ["matthew-1", "matthew-2", "luke-1", "luke-2"],
        "shared_anchor": "The birth of Jesus; the nativity; born in Bethlehem; the virgin Mary; the angel Gabriel; the shepherds; the wise men; the star; wrapped in swaddling clothes; laid in a manger",
    },
    "peter_denial": {
        "stories": ["matthew-26", "mark-14", "luke-22", "john-18"],
        "shared_anchor": "Peter denies Jesus three times; before the rooster crows; I do not know the man; Peter went out and wept bitterly",
    },
    "creation_and_fall": {
        "stories": ["genesis-1", "genesis-2", "genesis-3"],
        "shared_anchor": "God created the heavens and the earth; the garden of Eden; Adam and Eve; the serpent tempted Eve; they ate the forbidden fruit; the fall of man; cursed is the ground; dust you are and to dust you shall return",
    },
}

CORE_COLLECTIONS = {"old_testament", "new_testament"}

# Native Bible vocabulary patches: correct NLLB mistranslations with actual
# Bible terminology used by native speakers and established translations.
# Format: {lang: {"canonical_names": {sid: "prepend text"}, "verse_anchors": {sid: "prepend text"}}}
NATIVE_PATCHES = {
    "fr": {
        "canonical_names": {
            "ephesians-6": "L'armure de Dieu, revêtez-vous de toutes les armes de Dieu, ",
            "luke-15": "La parabole du fils prodigue, le fils prodigue, ",
            "luke-10": "Le bon Samaritain, la parabole du bon Samaritain, ",
            "exodus-3": "Le buisson ardent, Moïse et le buisson ardent, ",
            "john-3": "Car Dieu a tant aimé le monde, Nicodème, ",
            "genesis-6": "L'arche de Noé, le déluge, ",
            "genesis-11": "La tour de Babel, ",
            "genesis-37": "Le manteau de Joseph, Joseph vendu par ses frères, ",
            "genesis-1": "La création du monde en six jours, Dieu créa les cieux et la terre, au commencement, ",
            "genesis-2": "Le jardin d'Éden, Adam et Ève, l'homme et la femme, ",
            "exodus-14": "La traversée de la mer Rouge, Moïse ouvre la mer, ",
            "exodus-20": "Les dix commandements, les tables de la loi, ",
            "exodus-32": "Le veau d'or, ",
            "joshua-6": "Les murailles de Jéricho, ",
            "judges-16": "Samson et Dalila, l'homme le plus fort, ",
            "daniel-6": "Daniel dans la fosse aux lions, ",
            "daniel-3": "La fournaise ardente, Shadrach Meshach et Abed-Nego, ",
            "matthew-28": "La résurrection de Jésus, le tombeau vide, Jésus est ressuscité des morts le troisième jour, la résurrection au matin du troisième jour, les femmes au tombeau vide, l'ange dit il est ressuscité, ",
            "matthew-17": "La transfiguration de Jésus, son visage resplendit comme le soleil, Moïse et Élie apparurent, ",
            "mark-9": "La transfiguration de Jésus sur la montagne, ",
            "matthew-14": "La multiplication des pains, Jésus marche sur l'eau, ",
            "john-11": "La résurrection de Lazare, Lazare reviens, ",
            "acts-2": "La Pentecôte, la venue du Saint-Esprit, les langues de feu, ils furent tous remplis du Saint-Esprit, ",
            "acts-9": "La conversion de Saul sur le chemin de Damas, ",
            "revelation-13": "La marque de la bête, le nombre 666, ",
            "revelation-6": "Les quatre cavaliers de l'Apocalypse, ",
            "psalms-23": "L'Éternel est mon berger, le psaume du berger, ",
            "romans-8": "Rien ne pourra nous séparer de l'amour de Dieu, ceux qu'il a prédestinés il les a aussi appelés, ",
            "ephesians-1": "La prédestination, choisis en Christ avant la fondation du monde, élection et vocation, ",
            "malachi-3": "La dîme, apportez toutes les dîmes, ",
            "1-kings-18": "Élie au mont Carmel, le feu du ciel, ",
            "1-kings-17": "Élie nourri par les corbeaux, ",
            "genesis-32": "Jacob lutte avec Dieu, le combat de Jacob, ",
            "1-samuel-17": "David et Goliath, le berger et le géant, ",
            "matthew-26": "La Cène, le dernier repas, le pain et le vin, ",
            "matthew-6": "Le Notre Père, la prière du Seigneur, ne vous inquiétez pas, ",
            "philippians-4": "Ne vous inquiétez de rien, je puis tout par celui qui me fortifie, ",
            "isaiah-53": "Le serviteur souffrant, blessé pour nos péchés, ",
            "jeremiah-31": "La nouvelle alliance, la loi dans les coeurs, ",
            "galatians-5": "Le fruit de l'Esprit, amour joie paix, ",
            "1-corinthians-15": "La résurrection des morts, ô mort où est ta victoire, ",
            "hebrews-11": "La foi est la ferme assurance, par la foi, ",
            "proverbs-3": "Confie-toi en l'Éternel de tout ton coeur, ",
            "matthew-5": "Les Béatitudes, le Sermon sur la montagne, ",
            "john-14": "Je suis le chemin la vérité et la vie, ",
            "romans-3": "Tous ont péché, ",
            "ephesians-2": "C'est par la grâce que vous êtes sauvés, par la grâce et non par les oeuvres, ",
            "2chronicles-7": "Si mon peuple sur qui est invoqué mon nom s'humilie et prie, je pardonnerai leur péché et je guérirai leur pays, ",
            "titus-3": "Il nous a sauvés non par les oeuvres de justice mais selon sa miséricorde, ",
            "acts-3": "Pierre guérit le boiteux à la porte du temple, lève-toi et marche, ",
            "isaiah-41": "Ne crains point car je suis avec toi, ne sois pas effrayé car je suis ton Dieu, ",
            "genesis-3": "Adam et Ève mangent le fruit défendu, la chute de l'homme, le péché originel, le serpent tente Ève dans le jardin d'Éden, ",
            "genesis-28": "L'échelle de Jacob, le songe de Jacob à Béthel, les anges montent et descendent sur une échelle, ",
            "jeremiah-29": "Car je connais les projets que j'ai formés sur vous, des projets de paix et non de malheur, pour vous donner un avenir et une espérance, la lettre aux exilés, ",
            "job-1": "Les souffrances de Job, Job perd tout, l'Éternel a donné l'Éternel a ôté, ",
            "job-2": "La patience de Job, Job frappé de maladie, pourquoi les justes souffrent, ",
            "acts-8": "Le baptême dans l'eau, Philippe baptise l'Éthiopien, le baptême d'eau, ",
            "romans-6": "Le baptême dans l'eau, ensevelis avec Christ par le baptême, baptisés dans sa mort, ",
            "mark-16": "La résurrection de Jésus, le tombeau vide, il est ressuscité, ",
            "luke-24": "La résurrection de Jésus, les disciples sur le chemin d'Emmaüs, il est vivant, ",
            "mark-6": "Jésus marche sur l'eau, la multiplication des pains, Jésus nourrit cinq mille hommes, ",
            "john-6": "Jésus marche sur les eaux, le pain de vie, la multiplication des pains et des poissons, ",
            "mark-1": "Le baptême de Jésus par Jean dans le Jourdain, ",
            "luke-3": "Le baptême de Jésus dans le Jourdain, Jean-Baptiste, ",
            "mark-14": "La Cène, la Sainte Cène, le pain et le vin, la dernière nuit, ",
            "luke-22": "La Cène, la Sainte Cène, le reniement de Pierre, ",
            "1-corinthians-11": "La Sainte Cène, la communion, faites ceci en mémoire de moi, ",
            "colossians-3": "Le pardon, pardonnez-vous réciproquement, comme le Seigneur vous a pardonné, revêtez-vous de compassion, ",
        },
        "verse_anchors": {
            "john-3": "Car Dieu a tant aimé le monde qu'il a donné son Fils unique; ",
            "psalms-23": "L'Éternel est mon berger je ne manquerai de rien; quand je marche dans la vallée de l'ombre de la mort; ",
            "john-14": "Je suis le chemin la vérité et la vie nul ne vient au Père que par moi; ",
            "romans-3": "Car tous ont péché et sont privés de la gloire de Dieu; ",
            "philippians-4": "Ne vous inquiétez de rien mais en toute chose par la prière faites connaître vos besoins à Dieu; je puis tout par celui qui me fortifie; ",
            "matthew-6": "Notre Père qui es aux cieux que ton nom soit sanctifié; ne vous inquiétez donc pas du lendemain; cherchez premièrement le royaume de Dieu; ",
            "ephesians-2": "Car c'est par la grâce que vous êtes sauvés par le moyen de la foi et cela ne vient pas de vous c'est le don de Dieu ce n'est point par les oeuvres; ",
            "1-corinthians-13": "L'amour est patient l'amour est plein de bonté; ",
            "acts-2": "Le jour de la Pentecôte ils furent tous remplis du Saint-Esprit et se mirent à parler en d'autres langues; des langues comme de feu se posèrent sur chacun d'eux; ",
            "2chronicles-7": "Si mon peuple sur qui est invoqué mon nom s'humilie prie et cherche ma face et s'il se détourne de ses mauvaises voies je pardonnerai son péché; ",
            "romans-8": "Ceux qu'il a prédestinés il les a aussi appelés; ceux qu'il a appelés il les a aussi justifiés; rien ne pourra nous séparer de l'amour de Dieu; la prédestination et l'appel de Dieu; les prédestinés les appelés les élus; ",
            "ephesians-1": "Choisis en lui avant la fondation du monde; prédestinés selon le dessein de celui qui opère toutes choses; élus selon la prescience de Dieu; ",
            "matthew-28": "Il est ressuscité il n'est point ici; allez dire à ses disciples qu'il est ressuscité des morts; la résurrection de Jésus au troisième jour; ",
            "genesis-3": "Le serpent séduisit la femme et elle mangea du fruit; Adam et Ève mangèrent le fruit défendu; tu es poussière et tu retourneras dans la poussière; ",
            "genesis-28": "Jacob eut un songe et voici une échelle était appuyée sur la terre et son sommet touchait au ciel; les anges de Dieu montaient et descendaient; il appela ce lieu Béthel; ",
            "jeremiah-29": "Car je connais les projets que j'ai formés sur vous dit l'Éternel; projets de paix et non de malheur; afin de vous donner un avenir et de l'espérance; ",
            "acts-2": "Le baptême du Saint-Esprit; la Pentecôte; ils furent tous remplis du Saint-Esprit; le baptême de l'Esprit; ",
            "romans-6": "Ensevelis avec lui par le baptême; baptisés dans sa mort; le baptême dans l'eau; ",
        },
    },
    "ar": {
        "canonical_names": {
            "exodus-3": "العليقة المشتعلة, موسى والعليقة, ",
            "luke-15": "مثل الابن الضال, الابن الضال, ",
            "luke-10": "السامري الصالح, مثل السامري الصالح, ",
            "ephesians-6": "سلاح الله الكامل, البسوا سلاح الله الكامل, ",
            "john-3": "لانه هكذا احب الله العالم, نيقوديموس, ",
            "genesis-6": "فلك نوح, الطوفان العظيم, ",
            "genesis-11": "برج بابل, ",
            "genesis-37": "يوسف والرداء الملون, بيع يوسف من اخوته, ",
            "exodus-14": "شق البحر الاحمر, عبور البحر الاحمر, ",
            "exodus-20": "الوصايا العشر, الشريعة في سيناء, ",
            "exodus-32": "عبادة العجل الذهبي, ",
            "joshua-6": "سقوط اسوار اريحا, ",
            "joshua-3": "عبور نهر الاردن الى ارض كنعان, تابوت العهد يقود الشعب عبر النهر, دخول ارض الموعد, ",
            "judges-16": "شمشون ودليلة, اقوى رجل, ",
            "daniel-6": "دانيال في جب الاسود, ",
            "daniel-3": "اتون النار المتقدة, شدرخ وميشخ وعبدنغو في النار, الرجل الرابع في الاتون, الملك نبوخذنصر القى ثلاثة رجال في النار, ",
            "matthew-28": "قيامة يسوع المسيح, القبر الفارغ, قام من الاموات في اليوم الثالث, ",
            "matthew-14": "اطعام الخمسة آلاف, يسوع يمشي على الماء, المشي فوق امواج البحر, ",
            "john-11": "اقامة لعازر من الموت, ",
            "acts-9": "اهتداء شاول على طريق دمشق, ",
            "revelation-13": "علامة الوحش, الرقم 666, ",
            "revelation-6": "الفرسان الاربعة, ",
            "psalms-23": "الرب راعي فلا يعوزني شيء, مزمور الراعي, في وادي ظل الموت, ",
            "romans-8": "لا شيء يفصلنا عن محبة الله, ",
            "malachi-3": "العشور, هاتوا جميع العشور الى الخزنة, ",
            "1-kings-18": "ايليا على جبل الكرمل, نار من السماء, ",
            "1-kings-17": "ايليا والغربان, ",
            "genesis-32": "يعقوب يصارع الله, مصارعة يعقوب, ",
            "genesis-3": "الحية في جنة عدن, الحية تكلم حواء, سقوط الانسان, الخطيئة الاولى, ",
            "1-samuel-17": "داود وجليات, الراعي والعملاق, ",
            "matthew-3": "معمودية يسوع المسيح, يوحنا المعمدان يعمد يسوع, معمودية في نهر الاردن, نزول الروح القدس كحمامة, ",
            "mark-1": "معمودية يسوع, يوحنا يعمد يسوع في الاردن, ",
            "luke-3": "معمودية يسوع في نهر الاردن, يوحنا المعمدان, ",
            "matthew-26": "العشاء الاخير, الخبز والخمر, انكار بطرس ليسوع, بطرس ينكر يسوع ثلاث مرات, ",
            "john-2": "عرس قانا الجليل, يسوع يحول الماء الى خمر, اول معجزة ليسوع, ",
            "matthew-6": "الصلاة الربانية, ابانا الذي في السماوات, لا تهتموا, ",
            "philippians-4": "لا تهتموا بشيء, استطيع كل شيء في المسيح, ",
            "isaiah-53": "عبد الرب المتألم, مجروح لاجل معاصينا, ",
            "jeremiah-31": "العهد الجديد, اجعل شريعتي في داخلهم, ",
            "galatians-5": "ثمر الروح القدس محبة فرح سلام, ",
            "1-corinthians-15": "قيامة الاموات, اين شوكتك يا موت, ",
            "hebrews-11": "الايمان هو الثقة بما يرجى, ",
            "proverbs-3": "توكل على الرب بكل قلبك, ",
            "matthew-5": "التطويبات, الموعظة على الجبل, ",
            "john-14": "انا هو الطريق والحق والحياة, ",
            "romans-3": "الجميع اخطأوا واعوزهم مجد الله, ",
            "ephesians-2": "بالنعمة انتم مخلصون بالايمان, ",
            "lamentations-3": "عظيمة هي امانتك, مراحمه لا تزول, ",
            "hebrews-9": "بدون سفك دم لا تحصل مغفرة, ",
            "deuteronomy-6": "اسمع يا اسرائيل الرب الهنا رب واحد, الشيماع, ",
            "matthew-18": "المغفرة, اغفر سبعين مرة سبع مرات, مثل العبد الذي لا يرحم, ",
            "colossians-3": "احتملوا بعضكم بعضا وسامحوا بعضكم بعضا, البسوا الانسان الجديد, ",
            "2chronicles-7": "ان تواضع شعبي وصلوا وطلبوا وجهي ورجعوا عن طرقهم الردية, ",
            "matthew-17": "تجلي يسوع على الجبل, وجهه اضاء كالشمس, التجلي, ",
            "joshua-10": "الشمس وقفت في وسط السماء, يشوع يأمر الشمس ان تقف, معركة جبعون, ",
            "isaiah-45": "تجثو لي كل ركبة ويحلف لي كل لسان, انا الرب وليس آخر, ",
            "isaiah-48": "نداء الله السيادي, عناد اسرائيل, ",
            "1_thessalonians-4": "الاختطاف, الاموات في المسيح سيقومون اولا, لقاء الرب في الهواء, ",
            "acts-3": "بطرس يشفي الاعرج عند باب الهيكل, قم وامش, ",
            "genesis-1": "الخلق, في البدء خلق الله السماوات والارض, خلق العالم في ستة ايام, ",
            "isaiah-41": "لا تخف لاني معك لا تتلفت لاني الهك, ",
        },
        "verse_anchors": {
            "john-3": "لانه هكذا احب الله العالم حتى بذل ابنه الوحيد; ",
            "psalms-23": "الرب راعي فلا يعوزني شيء في مراع خضر يربضني; في وادي ظل الموت لا اخاف شرا لانك انت معي; ",
            "john-14": "انا هو الطريق والحق والحياة ليس احد يأتي الى الآب الا بي; ",
            "romans-3": "اذ الجميع اخطأوا واعوزهم مجد الله; ",
            "philippians-4": "لا تهتموا بشيء بل في كل شيء بالصلاة والتضرع; استطيع كل شيء في المسيح الذي يقويني; ",
            "matthew-6": "ابانا الذي في السماوات ليتقدس اسمك; لا تهتموا للغد; اطلبوا اولا ملكوت الله; ",
            "ephesians-2": "لانكم بالنعمة مخلصون بالايمان وذلك ليس منكم هو عطية الله; ",
            "1-corinthians-13": "المحبة تتأنى وترفق; ",
            "lamentations-3": "مراحم الرب لا تزول انها جديدة في كل صباح عظيمة هي امانتك; ",
            "hebrews-9": "وبدون سفك دم لا تحصل مغفرة; ",
            "matthew-3": "فلما اعتمد يسوع صعد للوقت من الماء واذا السماوات قد انفتحت له فرأى روح الله نازلا مثل حمامة; هذا هو ابني الحبيب; ",
            "daniel-3": "ان كان ذلك فان الهنا الذي نعبده يستطيع ان ينجينا من اتون النار المتقدة; والرابع يشبه ابن الآلهة; ",
        },
    },
    "ja": {
        "canonical_names": {
            "luke-15": "放蕩息子のたとえ, ",
            "luke-10": "良きサマリヤ人のたとえ, ",
            "exodus-3": "燃える柴, モーセと燃える柴, ",
            "ephesians-6": "神のすべての武具, 神の武具を身に着けよ, ",
            "john-3": "ニコデモとの対話, 神はそのひとり子を賜ったほどに世を愛された, 神は世を愛された, ",
            "genesis-6": "ノアの箱舟, 大洪水, ",
            "genesis-11": "バベルの塔, ",
            "genesis-37": "ヨセフの色とりどりの衣, 兄弟に売られたヨセフ, ",
            "exodus-14": "紅海を渡る, モーセが海を分ける, ",
            "exodus-20": "十戒, モーセの十戒, シナイ山の律法, ",
            "exodus-32": "金の子牛, ",
            "joshua-6": "エリコの壁, エリコの城壁が崩れる, ",
            "judges-16": "サムソンとデリラ, 最も力の強い男, ",
            "daniel-6": "ダニエルと獅子の穴, ",
            "daniel-3": "燃える炉, シャデラク メシャク アベデネゴ, ",
            "matthew-28": "イエスの復活, 空の墓, 復活の朝, ",
            "matthew-14": "五千人の給食, 五千人を養う, 五つのパンと二匹の魚, 五千人に食べ物を与える, 水の上を歩くイエス, ",
            "john-11": "ラザロのよみがえり, ラザロ出てきなさい, ",
            "acts-9": "サウロの回心, ダマスコへの途上, ",
            "revelation-13": "獣の刻印, 666の数字, ",
            "revelation-6": "黙示録の四騎士, ",
            "psalms-23": "主は私の羊飼い, 羊飼いの詩篇, ",
            "romans-8": "あらかじめ定められた者たちを召し, 何ものも神の愛から引き離すことはできない, ",
            "malachi-3": "十分の一献金, 什一のささげ物, ",
            "1-kings-18": "カルメル山のエリヤ, 天からの火, バアルの預言者, ",
            "1-kings-17": "カラスに養われたエリヤ, ",
            "genesis-32": "ヤコブの格闘, 神と格闘したヤコブ, ",
            "1-samuel-17": "ダビデとゴリアテ, 羊飼いの少年と巨人, ",
            "matthew-26": "最後の晩餐, パンとぶどう酒, ",
            "matthew-6": "主の祈り, 天にまします我らの父よ, 思い煩うな, ",
            "philippians-4": "何も思い煩わないで, 私を強くしてくださる方によって, 私を強くしてくださる方によってどんなことでもできる, すべてのことができる, ",
            "isaiah-53": "苦難のしもべ, 彼はさげすまれ, 我々の背きの故に刺し貫かれ, ",
            "jeremiah-31": "新しい契約, 心に律法を書く, ",
            "galatians-5": "御霊の実, 愛 喜び 平安, ",
            "1-corinthians-15": "死者の復活, 死よ おまえの勝利はどこにあるのか, ",
            "hebrews-11": "信仰とは望んでいる事がらを確信し, ",
            "proverbs-3": "心を尽くして主に信頼せよ, ",
            "matthew-5": "山上の垂訓, 八福の教え, 心の貧しい者は幸い, ",
            "john-14": "わたしは道であり真理であり命である, ",
            "romans-3": "すべての人は罪を犯した, ",
            "ephesians-2": "あなたがたは恵みにより信仰によって救われた, ",
            "lamentations-3": "主のいつくしみは絶えることがない, 朝ごとに新しい, ",
            "hebrews-9": "血を流すことなしには罪の赦しはない, ",
            "deuteronomy-6": "聞けイスラエルよ主は我々の神唯一の主である, シェマ, ",
            "jonah-1": "ヨナと大きな魚, ヨナが魚に飲み込まれた, ",
            "ruth-2": "ルツの落ち穂拾い, ボアズの畑で, ",
            "1-kings-3": "ソロモンの知恵, 神に知恵を求めたソロモン, ",
            "acts-3": "ペテロが足なえを癒す, 起きて歩け, 宮の門の乞食, ",
            "matthew-17": "イエスの変貌, 顔は太陽のように輝き, モーセとエリヤが現れた, ",
            "matthew-3": "イエスの洗礼, バプテスマのヨハネがヨルダン川でイエスに洗礼を授けた, 御霊が鳩のように降った, ",
            "joshua-3": "ヨルダン川を渡る, 契約の箱が民を導いた, 約束の地に入る, ",
            "ephesians-1": "予定論, 世界の基が据えられる前からキリストにあって選ばれた, ",
            "2chronicles-7": "わたしの名をもって呼ばれるわたしの民がへりくだり祈るなら, わたしはその地をいやそう, ",
            "joshua-10": "太陽よ止まれ, ヨシュアが太陽を止めた, ギベオンの戦い, 太陽が天の中に止まった, ",
            "isaiah-45": "すべてのひざはわたしにかがみ すべての舌は誓う, わたしは主であってほかにはない, ",
            "isaiah-48": "神の主権的な召し, イスラエルの頑なさ, ",
            "1_thessalonians-4": "携挙, キリストにあって死んだ者がまず最初によみがえり, 空中で主に会う, ",
            "genesis-1": "天地創造, 初めに神は天と地を創造された, 六日間の創造, ",
            "isaiah-41": "恐れるな わたしがあなたと共にいるから, うろたえるな わたしがあなたの神だから, ",
        },
        "verse_anchors": {
            "john-3": "神はそのひとり子を賜ったほどに世を愛された; 神は世を愛された; それは御子を信じる者が永遠の命を持つためである; ",
            "psalms-23": "主は私の羊飼い 私は乏しいことがない; 死の陰の谷を歩むとも; ",
            "john-14": "わたしは道であり真理であり命である わたしを通らなければ父のもとに行くことはできない; ",
            "romans-3": "すべての人は罪を犯したので神の栄光を受けられなくなった; ",
            "philippians-4": "何も思い煩わないで; 私を強くしてくださる方によって何でもすることができる; 私を強くしてくださる方によってどんなことでもできる; ",
            "matthew-6": "天にまします我らの父よ 願わくは御名をあがめさせたまえ; 明日のことを思い煩うな; まず神の国と神の義とを求めなさい; ",
            "ephesians-2": "あなたがたは恵みのゆえに信仰によって救われたのです; ",
            "1-corinthians-13": "愛は寛容であり愛は親切です; ",
            "lamentations-3": "主のいつくしみは絶えることがない それは朝ごとに新しい あなたの真実は力強い; ",
            "hebrews-9": "血を流すことなしには罪の赦しはありえない; ",
            "isaiah-41": "恐れるな わたしがあなたと共にいる うろたえるな わたしがあなたの神だから; 恐れてはならない; ",
            "matthew-18": "人を赦す; 七十七回まで赦しなさい; 仲間を赦さなかった家来のたとえ; ゆるし; ",
            "galatians-5": "御霊の実は愛 喜び 平安 寛容 親切 善意 誠実 柔和 自制; 聖霊の実; ",
            "1-corinthians-15": "死者の復活; 死はどこにあるのか; 朽ちるものが朽ちないものを着る; 死後どうなるか; 復活のからだ; ",
        },
    },
    "ko": {
        "canonical_names": {
            "luke-15": "탕자의 비유, 돌아온 탕자, ",
            "luke-10": "선한 사마리아인의 비유, ",
            "ephesians-6": "하나님의 전신갑주, 전신갑주를 입으라, ",
            "john-3": "하나님이 세상을 이처럼 사랑하사, 니고데모, ",
            "genesis-6": "노아의 방주, 대홍수, ",
            "genesis-11": "바벨탑, ",
            "genesis-37": "요셉의 채색옷, 형제들에게 팔린 요셉, ",
            "exodus-14": "홍해를 건넘, 모세가 바다를 가르다, ",
            "exodus-20": "십계명, 시내산의 율법, ",
            "exodus-32": "금송아지, ",
            "joshua-6": "여리고 성벽, 여리고 성이 무너지다, ",
            "judges-16": "삼손과 들릴라, 가장 힘센 사람, ",
            "daniel-6": "다니엘과 사자굴, ",
            "daniel-3": "불 속의 세 사람, 사드락 메삭 아벳느고, ",
            "matthew-28": "예수님의 부활, 빈 무덤, ",
            "matthew-14": "오병이어, 물 위를 걸으신 예수님, ",
            "john-11": "나사로의 부활, 나사로야 나오라, ",
            "acts-9": "사울의 회심, 다메섹 도상에서, ",
            "revelation-13": "짐승의 표, 666, ",
            "revelation-6": "요한계시록의 네 기사, ",
            "psalms-23": "여호와는 나의 목자시니 내게 부족함이 없으리로다, ",
            "romans-8": "미리 정하신 그들을 또한 부르시고, 아무것도 우리를 그리스도의 사랑에서 끊을 수 없으리, ",
            "malachi-3": "십일조, 온전한 십일조를 창고에 가져오라, ",
            "1-kings-18": "갈멜산의 엘리야, 하늘에서 불이 내려오다, ",
            "1-kings-17": "까마귀에게 먹임을 받은 엘리야, ",
            "genesis-32": "야곱이 하나님과 씨름하다, ",
            "1-samuel-17": "다윗과 골리앗, 목동 소년과 거인, ",
            "matthew-26": "최후의 만찬, 떡과 포도주, ",
            "matthew-6": "주기도문, 하늘에 계신 우리 아버지, 염려하지 말라, ",
            "philippians-4": "아무 것도 염려하지 말고, 내게 능력 주시는 자 안에서, ",
            "isaiah-53": "고난의 종, 그는 멸시를 받아, 찔림은 우리의 허물 때문이요, ",
            "jeremiah-31": "새 언약, 마음에 율법을 기록하리라, ",
            "galatians-5": "성령의 열매, 사랑 희락 화평, ",
            "1-corinthians-15": "죽은 자의 부활, 사망아 너의 승리가 어디 있느냐, ",
            "hebrews-11": "믿음은 바라는 것들의 실상이요, ",
            "proverbs-3": "너는 마음을 다하여 여호와를 신뢰하라, ",
            "matthew-5": "산상수훈, 팔복, 심령이 가난한 자는 복이 있나니, ",
            "john-14": "내가 곧 길이요 진리요 생명이니, ",
            "ephesians-2": "너희는 그 은혜에 의하여 믿음으로 말미암아 구원을 받았으니, ",
            "genesis-1": "천지창조, 태초에 하나님이 천지를 창조하시니라, ",
            "genesis-3": "에덴동산의 뱀, 인간의 타락, 원죄, ",
            "acts-3": "베드로가 앉은뱅이를 고치다, 일어나 걸으라, 성전 미문 앞의 구걸하는 자, ",
            "matthew-17": "예수님의 변화산, 얼굴이 해 같이 빛나며, 모세와 엘리야가 나타나다, ",
            "matthew-3": "예수님의 세례, 세례 요한이 요단강에서 예수님에게 세례를 베풀다, 성령이 비둘기같이 내려오시더라, ",
            "joshua-3": "요단강을 건넘, 언약궤가 백성을 인도하다, 약속의 땅에 들어가다, ",
            "ephesians-1": "예정론, 창세 전에 그리스도 안에서 우리를 택하시고, ",
            "2chronicles-7": "내 이름으로 일컫는 내 백성이 겸손하여 기도하면, 내가 그 땅을 고치리라, ",
            "1-corinthians-13": "사랑은 오래 참고 사랑은 온유하며, 사랑의 찬가, ",
            "exodus-32": "금송아지, 백성이 우상을 만들다, ",
            "isaiah-41": "두려워하지 말라 내가 너와 함께함이니라, 놀라지 말라 나는 네 하나님이니라, ",
            "mark-6": "오병이어, 오천 명을 먹이심, 예수님이 물 위를 걸으시다, ",
            "luke-8": "풍랑을 잠잠하게 하심, 바람과 바다가 순종하다, 잠잠하라 고요하라, ",
            "acts-22": "사울의 회심 간증, 다메섹 도상의 빛, ",
            "acts-26": "사울의 회심, 아그립바 왕 앞에서의 변론, ",
            "john-5": "베데스다 연못의 병자, 일어나 네 자리를 들고 걸으라, ",
            "colossians-3": "용서, 서로 용서하라, 주께서 너희를 용서하신 것 같이, ",
            "isaiah-52": "고난의 종, 여호와의 팔이 누구에게 나타났느냐, ",
            "hebrews-9": "피 흘림이 없은즉 사함이 없느니라, 한 번 죽는 것은 사람에게 정해진 것이요 그 후에는 심판이 있으리니, ",
            "matthew-2": "예수님의 탄생, 동방 박사, 베들레헴의 별, 헤롯왕, ",
        },
        "verse_anchors": {
            "john-3": "하나님이 세상을 이처럼 사랑하사 독생자를 주셨으니; ",
            "psalms-23": "여호와는 나의 목자시니 내게 부족함이 없으리로다; 사망의 음침한 골짜기로 다닐지라도; ",
            "john-14": "내가 곧 길이요 진리요 생명이니 나로 말미암지 않고는 아버지께로 올 자가 없느니라; ",
            "philippians-4": "아무 것도 염려하지 말고 다만 모든 일에 기도와 간구로; 내게 능력 주시는 자 안에서 내가 모든 것을 할 수 있느니라; ",
            "ephesians-2": "너희는 그 은혜에 의하여 믿음으로 말미암아 구원을 받았으니; ",
            "colossians-3": "서로 용서하기를 주께서 너희를 용서하신 것 같이 너희도 그리하고; 사랑을 입으라 이는 완전하게 매는 띠니라; ",
            "hebrews-9": "한 번 죽는 것은 사람에게 정하신 것이요 그 후에는 심판이 있으리니; 피 흘림이 없은즉 사함이 없느니라; ",
        },
    },
    "ru": {
        "canonical_names": {
            "exodus-3": "Неопалимая купина, Моисей и горящий куст, ",
            "ephesians-6": "Всеоружие Божие, облекитесь во всеоружие Божие, ",
            "luke-10": "Добрый самарянин, притча о добром самарянине, ",
            "luke-15": "Притча о блудном сыне, блудный сын, ",
            "john-3": "Ибо так возлюбил Бог мир, Никодим, ",
            "genesis-6": "Ноев ковчег, Великий потоп, ",
            "genesis-37": "Иосиф и разноцветная одежда, братья продали Иосифа, ",
            "exodus-14": "Переход через Красное море, Моисей разделил море, ",
            "exodus-20": "Десять заповедей, Закон на горе Синай, ",
            "judges-16": "Самсон и Далида, самый сильный человек, ",
            "daniel-6": "Даниил во рву со львами, ",
            "daniel-3": "Огненная печь, Седрах Мисах и Авденаго, ",
            "matthew-28": "Воскресение Иисуса Христа, пустая гробница, ",
            "john-11": "Воскрешение Лазаря, Лазарь выйди вон, ",
            "acts-9": "Обращение Савла на дороге в Дамаск, ",
            "revelation-13": "Начертание зверя, число 666, ",
            "psalms-23": "Господь Пастырь мой я ни в чём не буду нуждаться, ",
            "malachi-3": "Десятина, принесите все десятины в дом хранилища, ",
            "1-kings-18": "Илия на горе Кармил, огонь с неба, ",
            "matthew-26": "Тайная вечеря, хлеб и вино, ",
            "matthew-6": "Молитва Отче наш, не заботьтесь о завтрашнем дне, ",
            "philippians-4": "Не заботьтесь ни о чём, всё могу в укрепляющем меня, ",
            "isaiah-53": "Страдающий раб, Он изъязвлён был за грехи наши, ",
            "galatians-5": "Плод духа, любовь радость мир, ",
            "1-corinthians-15": "Воскресение мёртвых, Смерть где твоё жало, ",
            "proverbs-3": "Надейся на Господа всем сердцем твоим, ",
            "matthew-5": "Нагорная проповедь, Блаженства, блаженны нищие духом, ",
            "john-14": "Я есмь путь и истина и жизнь, ",
            "ephesians-2": "Ибо благодатью вы спасены через веру, ",
            "genesis-1": "Сотворение мира, В начале сотворил Бог небо и землю, ",
            "genesis-3": "Змей в Эдемском саду, грехопадение, первородный грех, ",
            "acts-3": "Пётр исцеляет хромого, встань и ходи, нищий у ворот храма, ",
            "matthew-17": "Преображение Иисуса, лицо Его просияло как солнце, Моисей и Илия явились, ",
            "matthew-3": "Крещение Иисуса, Иоанн Креститель крестит Иисуса в Иордане, Дух Божий сходил как голубь, ",
            "joshua-3": "Переход через Иордан, ковчег завета вёл народ, вступление в землю обетованную, ",
            "romans-8": "Кого Он предопределил тех и призвал, ничто не может отлучить нас от любви Божией, ",
            "ephesians-1": "Предопределение, избраны во Христе прежде создания мира, ",
            "2chronicles-7": "Если смирится народ Мой и будет молиться, Я исцелю землю их, ",
            "matthew-14": "Насыщение пяти тысяч, Иисус идёт по воде, ",
            "exodus-32": "Золотой телец, народ сделал идола у горы Синай, ",
            "1-corinthians-13": "Любовь долготерпит милосердствует, гимн любви, ",
            "jeremiah-31": "Новый завет, вложу закон Мой в сердца их, ",
            "1-kings-17": "Илия и вороны, вдова из Сарепты, ",
            "isaiah-41": "Не бойся ибо Я с тобою, не смущайся ибо Я Бог твой, ",
            "genesis-2": "Эдемский сад, одна плоть, мужчина оставит отца и мать и прилепится к жене, брак в Библии, ",
            "ephesians-5": "Мужья любите своих жён как Христос возлюбил Церковь, брак, одна плоть, Христос и Церковь, ",
            "mark-6": "Иисус ходит по воде, насыщение пяти тысяч, ",
            "john-5": "Исцеление у купальни Вифезда, встань возьми постель твою и ходи, ",
            "colossians-3": "Прощение, прощайте друг друга как Господь простил вас, облекитесь в милосердие, ",
            "hebrews-9": "Без пролития крови не бывает прощения, человекам положено однажды умереть а потом суд, ",
            "luke-22": "Тайная вечеря, сие есть тело Моё, чаша нового завета, ",
            "john-18": "Отречение Петра, Пётр трижды отрёкся от Иисуса, прежде нежели пропоёт петух, ",
        },
        "verse_anchors": {
            "john-3": "Ибо так возлюбил Бог мир что отдал Сына Своего Единородного; ",
            "psalms-23": "Господь Пастырь мой я ни в чём не буду нуждаться; если я пойду и долиною смертной тени; ",
            "john-14": "Я есмь путь и истина и жизнь никто не приходит к Отцу как только через Меня; ",
            "philippians-4": "Не заботьтесь ни о чём но всегда в молитве и прошении; всё могу в укрепляющем меня Иисусе Христе; ",
            "ephesians-2": "Ибо благодатью вы спасены через веру и сие не от вас Божий дар; ",
            "1-corinthians-13": "Любовь долготерпит милосердствует; если я говорю языками ангельскими; а теперь пребывают сии три вера надежда любовь; гимн любви; ",
            "exodus-32": "Золотой телец; народ сделал себе идола у горы Синай; Моисей разбил скрижали; ",
            "genesis-2": "Потому оставит человек отца своего и мать свою и прилепится к жене своей; и будут двое одна плоть; ",
            "colossians-3": "Снисходя друг другу и прощая взаимно; как Христос простил вас так и вы; облекитесь в милосердие; ",
            "hebrews-9": "Человекам положено однажды умереть а потом суд; без пролития крови не бывает прощения; ",
        },
    },
    "zh-Hans": {
        "canonical_names": {
            "exodus-3": "燃烧的荆棘, 摩西与燃烧的荆棘丛, ",
            "luke-15": "浪子回头的比喻, 小儿子的比喻, ",
            "luke-10": "好撒玛利亚人的比喻, ",
            "john-3": "神爱世人, 尼哥底母, ",
            "genesis-6": "挪亚方舟, 大洪水, ",
            "genesis-37": "约瑟的彩衣, 被兄弟们卖的约瑟, ",
            "exodus-14": "过红海, 摩西分开红海, ",
            "exodus-20": "十诫, 西奈山的律法, ",
            "judges-16": "参孙和大利拉, 最强壮的人, ",
            "daniel-6": "但以理在狮子坑中, ",
            "daniel-3": "火窑, 沙得拉 米煞 亚伯尼歌, ",
            "matthew-28": "耶稣复活, 空坟墓, ",
            "john-11": "拉撒路复活, 拉撒路出来, ",
            "acts-9": "扫罗在大马士革路上悔改, ",
            "revelation-13": "兽的印记, 666, ",
            "psalms-23": "耶和华是我的牧者 我必不至缺乏, ",
            "malachi-3": "十分之一, 当纳的十分之一, ",
            "1-kings-18": "以利亚在迦密山上, 天降火, ",
            "matthew-26": "最后的晚餐, 饼和杯, ",
            "matthew-6": "主祷文, 我们在天上的父, 不要忧虑, ",
            "philippians-4": "应当一无挂虑, 我靠着那加给我力量的凡事都能做, ",
            "isaiah-53": "受苦的仆人, 他为我们的过犯受害, ",
            "galatians-5": "圣灵的果子, 仁爱 喜乐 和平, ",
            "1-corinthians-15": "死人复活, 死啊你得胜的权势在哪里, ",
            "proverbs-3": "你要专心仰赖耶和华, ",
            "matthew-5": "登山宝训, 八福, 虚心的人有福了, ",
            "john-14": "我就是道路真理生命, ",
            "ephesians-2": "你们得救是本乎恩也因着信, ",
            "matthew-17": "耶稣登山变像, 脸面明亮如日头, 摩西和以利亚向他们显现, ",
            "matthew-3": "耶稣受洗, 施洗约翰在约旦河给耶稣施洗, 圣灵仿佛鸽子降下, ",
            "joshua-3": "过约旦河, 约柜引领百姓过河, 进入应许之地, ",
            "romans-8": "预先所定下的人又召他们来, 什么都不能叫我们与神的爱隔绝, ",
            "ephesians-1": "预定论, 从创立世界以前在基督里拣选了我们, ",
            "2chronicles-7": "这称为我名下的子民若自卑祷告, 我必医治他们的地, ",
            "acts-3": "彼得医好瘸腿的人, 起来行走, 圣殿美门口的乞丐, ",
            "matthew-14": "五饼二鱼, 耶稣在水上行走, ",
            "exodus-32": "金牛犊, 百姓造了偶像, ",
            "1-corinthians-13": "爱是恒久忍耐又有恩慈, 爱的篇章, ",
            "jeremiah-31": "新约, 我要将我的律法放在他们里面写在他们心上, ",
            "1-kings-17": "以利亚被乌鸦供养, 撒勒法的寡妇, ",
            "isaiah-41": "你不要害怕因为我与你同在, 不要惊惶因为我是你的神, ",
        },
        "verse_anchors": {
            "john-3": "神爱世人甚至将他的独生子赐给他们; ",
            "psalms-23": "耶和华是我的牧者我必不至缺乏; 我虽然行过死荫的幽谷; ",
            "john-14": "我就是道路真理生命 若不藉着我 没有人能到父那里去; ",
            "philippians-4": "应当一无挂虑 只要凡事藉着祷告祈求; 我靠着那加给我力量的 凡事都能做; ",
            "ephesians-2": "你们得救是本乎恩也因着信 这并不是出于自己乃是神所赐的; ",
        },
    },
    "zh-Hant": {
        "canonical_names": {
            "exodus-3": "燃燒的荊棘, 摩西與燃燒的荊棘叢, ",
            "luke-15": "浪子回頭的比喻, 小兒子的比喻, ",
            "luke-10": "好撒瑪利亞人的比喻, ",
            "john-3": "神愛世人, 尼哥底母, ",
            "genesis-6": "挪亞方舟, 大洪水, ",
            "genesis-37": "約瑟的彩衣, 被兄弟們賣的約瑟, ",
            "exodus-14": "過紅海, 摩西分開紅海, ",
            "exodus-20": "十誡, 西奈山的律法, ",
            "judges-16": "參孫和大利拉, 最強壯的人, ",
            "daniel-6": "但以理在獅子坑中, ",
            "daniel-3": "火窯, 沙得拉 米煞 亞伯尼歌, ",
            "matthew-28": "耶穌復活, 空墳墓, ",
            "john-11": "拉撒路復活, 拉撒路出來, ",
            "acts-9": "掃羅在大馬士革路上悔改, ",
            "revelation-13": "獸的印記, 666, ",
            "psalms-23": "耶和華是我的牧者 我必不致缺乏, ",
            "malachi-3": "十分之一, 當納的十分之一, 送入倉庫, 十一奉獻, 萬軍之耶和華說你們要試試我, ",
            "matthew-26": "最後的晚餐, 餅和杯, ",
            "matthew-6": "主禱文, 我們在天上的父, 不要憂慮, ",
            "philippians-4": "應當一無掛慮, 我靠著那加給我力量的凡事都能做, ",
            "isaiah-53": "受苦的僕人, 他為我們的過犯受害, ",
            "galatians-5": "聖靈的果子, 仁愛 喜樂 和平, ",
            "1-corinthians-15": "死人復活, 死啊你得勝的權勢在哪裡, ",
            "proverbs-3": "你要專心仰賴耶和華, ",
            "matthew-5": "登山寶訓, 八福, 虛心的人有福了, ",
            "john-14": "我就是道路真理生命, ",
            "ephesians-2": "你們得救是本乎恩也因著信, ",
            "acts-3": "彼得醫好瘸腿的人, 起來行走, 聖殿美門口的乞丐, ",
            "matthew-17": "耶穌登山變像, 臉面明亮如日頭, 摩西和以利亞向他們顯現, ",
            "matthew-3": "耶穌受洗, 施洗約翰在約旦河給耶穌施洗, 聖靈彷彿鴿子降下, ",
            "joshua-3": "過約旦河, 約櫃引領百姓過河, 進入應許之地, ",
            "romans-8": "預先所定下的人又召他們來, 什麼都不能叫我們與神的愛隔絕, ",
            "ephesians-1": "預定論, 從創立世界以前在基督裡揀選了我們, ",
            "2chronicles-7": "這稱為我名下的子民若自卑禱告, 我必醫治他們的地, ",
            "genesis-1": "創造天地, 起初神創造天地, ",
            "matthew-14": "五餅二魚, 耶穌在水上行走, ",
            "exodus-32": "金牛犢, 百姓造了偶像, ",
            "1-corinthians-13": "愛是恆久忍耐又有恩慈, 愛的篇章, ",
            "jeremiah-31": "新約, 我要將我的律法放在他們裡面, 寫在他們心上, 我要作他們的神, 不再刻在石版上, ",
            "1-kings-17": "以利亞被烏鴉供養, 撒勒法的寡婦, ",
            "isaiah-41": "你不要害怕因為我與你同在, 不要驚惶因為我是你的神, ",
        },
        "verse_anchors": {
            "john-3": "神愛世人甚至將他的獨生子賜給他們; ",
            "psalms-23": "耶和華是我的牧者我必不致缺乏; 我雖然行過死蔭的幽谷; ",
            "john-14": "我就是道路真理生命 若不藉著我 沒有人能到父那裡去; ",
            "philippians-4": "應當一無掛慮 只要凡事藉著禱告祈求; 我靠著那加給我力量的 凡事都能做; ",
            "malachi-3": "你們要將當納的十分之一全然送入倉庫; 萬軍之耶和華說你們以此試試我是否為你們敞開天上的窗戶傾福與你們; 十一奉獻萬軍之耶和華試試我; ",
            "jeremiah-31": "我要將我的律法放在他們裡面寫在他們心上; 我要作他們的神他們要作我的子民; 新約刻在心上不是刻在石版上; ",
        },
    },
    "it": {
        "canonical_names": {
            "exodus-3": "Il roveto ardente, Mosè e il roveto ardente, ",
            "luke-15": "La parabola del figlio prodigo, il figliol prodigo, ",
            "luke-10": "Il buon Samaritano, la parabola del buon Samaritano, ",
            "ephesians-6": "L'armatura di Dio, rivestitevi della completa armatura di Dio, elmo della salvezza, scudo della fede, spada dello Spirito, ",
            "john-3": "Perché Dio ha tanto amato il mondo, Nicodemo, ",
            "genesis-6": "L'arca di Noè, il diluvio universale, ",
            "genesis-37": "Il mantello di Giuseppe, Giuseppe venduto dai fratelli, ",
            "exodus-14": "L'attraversamento del Mar Rosso, Mosè divide il mare, ",
            "exodus-20": "I dieci comandamenti, la legge sul Sinai, ",
            "judges-16": "Sansone e Dalila, l'uomo più forte, ",
            "daniel-6": "Daniele nella fossa dei leoni, ",
            "daniel-3": "La fornace ardente, Sadrac Mesac e Abednego, ",
            "matthew-28": "La risurrezione di Gesù, la tomba vuota, ",
            "john-11": "La risurrezione di Lazzaro, Lazzaro vieni fuori, ",
            "acts-9": "La conversione di Saulo sulla via di Damasco, ",
            "revelation-13": "Il marchio della bestia, il numero 666, ",
            "psalms-23": "Il Signore è il mio pastore non manco di nulla, ",
            "malachi-3": "La decima, portate tutte le decime, ",
            "1-kings-18": "Elia sul monte Carmelo, il fuoco dal cielo, ",
            "matthew-26": "L'Ultima Cena, il pane e il vino, ",
            "matthew-6": "Il Padre Nostro, non affannatevi, ",
            "philippians-4": "Non angustiatevi di nulla, io posso ogni cosa in colui che mi fortifica, ",
            "isaiah-53": "Il servo sofferente, trafitto per le nostre colpe, ",
            "galatians-5": "Il frutto dello Spirito, amore gioia pace, ",
            "1-corinthians-15": "La risurrezione dei morti, O morte dov'è la tua vittoria, ",
            "proverbs-3": "Confida nel Signore con tutto il cuore, ",
            "matthew-5": "Le Beatitudini, il Sermone sul monte, ",
            "john-14": "Io sono la via la verità e la vita, ",
            "ephesians-2": "Per grazia siete stati salvati mediante la fede, ",
            "genesis-1": "La creazione del mondo, In principio Dio creò i cieli e la terra, ",
            "genesis-3": "Il serpente nel giardino dell'Eden, la caduta dell'uomo, il peccato originale, ",
            "acts-3": "Pietro guarisce lo zoppo, alzati e cammina, nel nome di Gesù Cristo il Nazareno alzati e cammina, lo zoppo alla porta Bella, ",
            "matthew-17": "La trasfigurazione di Gesù, il suo volto brillò come il sole, Mosè ed Elia apparvero, ",
            "matthew-3": "Il battesimo di Gesù, Giovanni il Battista battezza Gesù nel Giordano, lo Spirito scese come una colomba, ",
            "joshua-3": "L'attraversamento del Giordano, l'arca dell'alleanza guidò il popolo, l'ingresso nella terra promessa, ",
            "romans-8": "Quelli che ha predestinati li ha pure chiamati, niente potrà separarci dall'amore di Dio, ",
            "ephesians-1": "La predestinazione, scelti in Cristo prima della fondazione del mondo, ",
            "2chronicles-7": "Se il mio popolo si umilia e prega, io guarirò il loro paese, ",
            "matthew-14": "La moltiplicazione dei pani e dei pesci, Gesù cammina sulle acque, Gesù cammina sul mare, Pietro cammina sull'acqua, vento contrario sul lago, ",
            "exodus-32": "Il vitello d'oro, il popolo fece un idolo, ",
            "1-corinthians-13": "L'amore è paziente è benigno, l'inno all'amore, ",
            "jeremiah-31": "La nuova alleanza, porrò la mia legge nel loro cuore, ",
            "1-kings-17": "Elia nutrito dai corvi, la vedova di Sarepta, ",
            "isaiah-41": "Non temere perché io sono con te, non ti sgomentare perché io sono il tuo Dio, ",
            "genesis-2": "Una sola carne, l'uomo lascerà suo padre e sua madre e si unirà a sua moglie, marito e moglie, il matrimonio nella Bibbia, ",
            "ephesians-5": "Mariti amate le vostre mogli come Cristo ha amato la chiesa, il matrimonio nella Bibbia, Cristo e la chiesa, una sola carne, ",
        },
        "verse_anchors": {
            "john-3": "Perché Dio ha tanto amato il mondo che ha dato il suo unigenito Figlio; ",
            "psalms-23": "Il Signore è il mio pastore non manco di nulla; quand'anche camminassi nella valle dell'ombra della morte; ",
            "john-14": "Io sono la via la verità e la vita nessuno viene al Padre se non per mezzo di me; ",
            "philippians-4": "Non angustiatevi di nulla ma in ogni cosa fate conoscere le vostre richieste a Dio; io posso ogni cosa in colui che mi fortifica; ",
            "ephesians-2": "Perché è per grazia che siete stati salvati mediante la fede e ciò non viene da voi è il dono di Dio; ",
            "genesis-2": "L'uomo lascerà suo padre e sua madre e si unirà a sua moglie e saranno una sola carne; il matrimonio istituito da Dio; ",
            "ephesians-5": "Mariti amate le vostre mogli come Cristo ha amato la chiesa e ha dato se stesso per lei; questo mistero è grande io dico rispetto a Cristo e alla chiesa; ",
            "acts-3": "Nel nome di Gesù Cristo il Nazareno alzati e cammina; Pietro prese lo zoppo per la mano destra e lo sollevò; alla porta del tempio chiamata Bella; ",
        },
    },
    "pt": {
        "canonical_names": {
            "exodus-3": "A sarça ardente, Moisés e a sarça ardente, ",
            "luke-15": "A parábola do filho pródigo, o filho pródigo, ",
            "luke-10": "O bom Samaritano, a parábola do bom Samaritano, ",
            "ephesians-6": "A armadura de Deus, revesti-vos de toda a armadura de Deus, ",
            "john-3": "Porque Deus amou o mundo de tal maneira, Nicodemos, ",
            "genesis-6": "A arca de Noé, o dilúvio, ",
            "genesis-37": "A túnica de José, José vendido pelos irmãos, ",
            "exodus-14": "A travessia do Mar Vermelho, Moisés abre o mar, ",
            "exodus-20": "Os dez mandamentos, a lei no Sinai, ",
            "judges-16": "Sansão e Dalila, o homem mais forte, ",
            "daniel-6": "Daniel na cova dos leões, ",
            "daniel-3": "A fornalha ardente, Sadraque Mesaque e Abede-Nego, ",
            "matthew-28": "A ressurreição de Jesus, o túmulo vazio, ",
            "john-11": "A ressurreição de Lázaro, Lázaro sai para fora, ",
            "acts-9": "A conversão de Saulo no caminho de Damasco, ",
            "revelation-13": "A marca da besta, o número 666, ",
            "psalms-23": "O Senhor é o meu pastor nada me faltará, ",
            "malachi-3": "O dízimo, trazei todos os dízimos à casa do tesouro, ",
            "1-kings-18": "Elias no monte Carmelo, fogo do céu, ",
            "matthew-26": "A Última Ceia, o pão e o vinho, ",
            "matthew-6": "A Oração do Pai Nosso, não andeis ansiosos, ",
            "philippians-4": "Não andeis ansiosos de coisa alguma, tudo posso naquele que me fortalece, ",
            "isaiah-53": "O servo sofredor, ferido pelas nossas transgressões, ",
            "galatians-5": "O fruto do Espírito, amor alegria paz, ",
            "1-corinthians-15": "A ressurreição dos mortos, Onde está ó morte a tua vitória, ",
            "proverbs-3": "Confia no Senhor de todo o teu coração, ",
            "matthew-5": "As Bem-aventuranças, o Sermão da Montanha, ",
            "john-14": "Eu sou o caminho a verdade e a vida, ",
            "ephesians-2": "Pela graça sois salvos mediante a fé, ",
            "genesis-1": "A criação do mundo, Deus criou os céus e a terra, no princípio, ",
            "genesis-3": "A serpente no jardim do Éden, a queda do homem, o pecado original, ",
            "acts-3": "Pedro cura o coxo, levanta-te e anda, o mendigo à porta do templo, ",
            "matthew-17": "A transfiguração de Jesus, o rosto resplandeceu como o sol, Moisés e Elias apareceram, ",
            "matthew-3": "O batismo de Jesus, João Batista batiza Jesus no rio Jordão, o Espírito desceu como pomba, ",
            "joshua-3": "A travessia do rio Jordão, a arca da aliança guiou o povo, a entrada na terra prometida, ",
            "romans-8": "Os que predestinou também chamou, nada poderá nos separar do amor de Deus, ",
            "ephesians-1": "A predestinação, escolhidos em Cristo antes da fundação do mundo, ",
            "2chronicles-7": "Se o meu povo se humilhar e orar, eu sararei a sua terra, ",
            "matthew-14": "A multiplicação dos pães e peixes, Jesus anda sobre as águas, ",
            "exodus-32": "O bezerro de ouro, o povo fez um ídolo, ",
            "1-corinthians-13": "O amor é paciente é bondoso, o capítulo do amor, ",
            "jeremiah-31": "A nova aliança, porei a minha lei no seu coração, ",
            "1-kings-17": "Elias alimentado pelos corvos, a viúva de Sarepta, ",
            "isaiah-41": "Não temas porque eu sou contigo, não te assombres porque eu sou teu Deus, ",
        },
        "verse_anchors": {
            "john-3": "Porque Deus amou o mundo de tal maneira que deu o seu Filho unigênito; ",
            "psalms-23": "O Senhor é o meu pastor nada me faltará; ainda que eu andasse pelo vale da sombra da morte; ",
            "john-14": "Eu sou o caminho a verdade e a vida ninguém vem ao Pai senão por mim; ",
            "philippians-4": "Não andeis ansiosos de coisa alguma em tudo porém sejam conhecidas as vossas petições diante de Deus pela oração; tudo posso naquele que me fortalece; ",
            "ephesians-2": "Porque pela graça sois salvos mediante a fé e isto não vem de vós é dom de Deus; ",
            "genesis-1": "No princípio criou Deus os céus e a terra; a criação do mundo em seis dias; Deus disse haja luz e houve luz; criou o homem à sua imagem; ",
            "1-corinthians-13": "O amor é paciente o amor é bondoso; ainda que eu fale as línguas dos anjos; o capítulo do amor; agora permanecem a fé a esperança e o amor; ",
            "romans-6": "O salário do pecado é a morte mas o dom gratuito de Deus é a vida eterna; ",
        },
    },
    "hi": {
        "canonical_names": {
            "luke-10": "अच्छे सामरी का दृष्टान्त, भले सामरी की कहानी, ",
            "luke-15": "उड़ाऊ पुत्र का दृष्टान्त, उड़ाऊ बेटे की कहानी, ",
            "exodus-3": "जलती हुई झाड़ी, मूसा और जलती झाड़ी, ",
            "ephesians-6": "परमेश्वर के सारे हथियार, परमेश्वर के हथियार बांध लो, ",
            "john-3": "क्योंकि परमेश्वर ने जगत से ऐसा प्रेम रखा, नीकुदेमुस, ",
            "genesis-6": "नूह का जहाज़, महाजलप्रलय, ",
            "genesis-37": "यूसुफ का रंगीन कोट, भाइयों ने यूसुफ को बेचा, ",
            "exodus-14": "लाल समुद्र को पार करना, मूसा ने समुद्र को दो भागों में बांटा, ",
            "exodus-20": "दस आज्ञाएं, सीनै पर्वत की व्यवस्था, ",
            "judges-16": "शिमशोन और दलीला, सबसे ताकतवर आदमी, ",
            "daniel-6": "दानिय्येल शेरों की मांद में, ",
            "daniel-3": "आग का भट्ठा, शद्रक मेशक और अबेदनगो, ",
            "matthew-28": "यीशु का पुनरुत्थान, खाली कब्र, ",
            "john-11": "लाज़र का जी उठना, लाज़र बाहर आ, ",
            "acts-9": "दमिश्क के रास्ते पर शाऊल का परिवर्तन, ",
            "revelation-13": "पशु की छाप, 666 की संख्या, ",
            "psalms-23": "यहोवा मेरा चरवाहा है मुझे कमी न होगी, ",
            "malachi-3": "दशमांश, सारा दशमांश भण्डार में ले आओ, ",
            "matthew-26": "अंतिम भोज, रोटी और दाखमधु, ",
            "matthew-6": "प्रभु की प्रार्थना, हे हमारे पिता, चिंता मत करो, ",
            "philippians-4": "किसी बात की चिंता मत करो, मुझे बल देनेवाले में सब कुछ कर सकता हूं, ",
            "isaiah-53": "दुखी सेवक, वह हमारे अपराधों के कारण घायल किया गया, ",
            "galatians-5": "आत्मा का फल, प्रेम आनन्द शान्ति, ",
            "1-corinthians-15": "मृतकों का पुनरुत्थान, हे मृत्यु तेरी जय कहां, ",
            "proverbs-3": "अपने सम्पूर्ण हृदय से यहोवा पर भरोसा रख, ",
            "matthew-5": "पहाड़ी उपदेश, धन्य वचन, धन्य हैं वे जो मन के दीन हैं, ",
            "john-14": "मार्ग और सत्य और जीवन मैं ही हूं, ",
            "ephesians-2": "अनुग्रह ही से तुम्हारा उद्धार विश्वास के द्वारा हुआ है, ",
            "genesis-3": "अदन की वाटिका में सांप, मनुष्य का पतन, आदि पाप, ",
            "acts-3": "पतरस ने लंगड़े को चंगा किया, उठ और चल, मंदिर के द्वार पर भिखारी, ",
            "matthew-17": "यीशु का रूपान्तरण, उसका चेहरा सूरज की तरह चमका, मूसा और एलिय्याह प्रकट हुए, ",
            "matthew-3": "यीशु का बपतिस्मा, यूहन्ना बपतिस्मा देनेवाले ने यरदन नदी में यीशु को बपतिस्मा दिया, पवित्र आत्मा कबूतर की तरह उतरा, ",
            "joshua-3": "यरदन नदी पार करना, वाचा का सन्दूक ने लोगों को ले जाया, प्रतिज्ञा की भूमि में प्रवेश, ",
            "romans-8": "जिन्हें उसने ठहराया उन्हें बुलाया भी, कोई वस्तु हमें परमेश्वर के प्रेम से अलग नहीं कर सकती, ",
            "ephesians-1": "पूर्वनियुक्ति, जगत की उत्पत्ति से पहले मसीह में चुने गए, ",
            "2chronicles-7": "यदि मेरी प्रजा दीन होकर प्रार्थना करे, तो मैं उनके देश को चंगा करूंगा, ",
            "genesis-1": "सृष्टि, आदि में परमेश्वर ने आकाश और पृथ्वी की सृष्टि की, ",
            "matthew-14": "पांच हजार को खिलाना, यीशु पानी पर चलते हैं, ",
            "exodus-32": "सोने का बछड़ा, लोगों ने मूर्ति बनाई, ",
            "1-corinthians-13": "प्रेम धीरजवान और कृपालु है, प्रेम का अध्याय, ",
            "jeremiah-31": "नई वाचा, मैं अपनी व्यवस्था उनके हृदय पर लिखूंगा, ",
            "1-kings-17": "एलिय्याह को कौवों ने खिलाया, सारपत की विधवा, ",
            "isaiah-41": "मत डर क्योंकि मैं तेरे साथ हूं, भय न खा क्योंकि मैं तेरा परमेश्वर हूं, ",
        },
        "verse_anchors": {
            "john-3": "क्योंकि परमेश्वर ने जगत से ऐसा प्रेम रखा कि उसने अपना एकलौता पुत्र दे दिया; ",
            "psalms-23": "यहोवा मेरा चरवाहा है मुझे कमी न होगी; चाहे मैं मृत्यु की छाया की तराई में चलूं; ",
            "john-14": "मार्ग और सत्य और जीवन मैं ही हूं बिना मेरे द्वारा कोई पिता के पास नहीं पहुंच सकता; ",
            "philippians-4": "किसी बात की चिंता मत करो परन्तु हर एक बात में प्रार्थना और बिनती द्वारा; मुझे बल देनेवाले में सब कुछ कर सकता हूं; ",
            "ephesians-2": "क्योंकि विश्वास के द्वारा अनुग्रह ही से तुम्हारा उद्धार हुआ है; ",
        },
    },
    "es": {
        "canonical_names": {
            "exodus-3": "La zarza ardiente, Moisés y la zarza ardiente, ",
            "luke-15": "La parábola del hijo pródigo, el hijo pródigo, ",
            "luke-10": "El buen samaritano, la parábola del buen samaritano, ",
            "ephesians-6": "La armadura de Dios, vestíos de toda la armadura de Dios, ",
            "john-3": "Porque de tal manera amó Dios al mundo, Nicodemo, ",
            "genesis-6": "El arca de Noé, el diluvio, ",
            "genesis-37": "La túnica de José, José vendido por sus hermanos, ",
            "exodus-14": "El cruce del Mar Rojo, Moisés abre el mar, ",
            "exodus-20": "Los diez mandamientos, la ley en el Sinaí, ",
            "judges-16": "Sansón y Dalila, el hombre más fuerte, ",
            "daniel-6": "Daniel en el foso de los leones, ",
            "daniel-3": "El horno de fuego, Sadrac Mesac y Abed-nego, ",
            "matthew-28": "La resurrección de Jesús, la tumba vacía, ",
            "john-11": "La resurrección de Lázaro, Lázaro ven fuera, ",
            "acts-9": "La conversión de Saulo en el camino a Damasco, ",
            "revelation-13": "La marca de la bestia, el número 666, ",
            "psalms-23": "Jehová es mi pastor nada me faltará, ",
            "malachi-3": "El diezmo, traed todos los diezmos al alfolí, ",
            "1-kings-18": "Elías en el monte Carmelo, fuego del cielo, ",
            "matthew-26": "La Última Cena, el pan y el vino, ",
            "matthew-6": "El Padre Nuestro, no os afanéis, ",
            "philippians-4": "Por nada estéis afanosos, todo lo puedo en Cristo que me fortalece, ",
            "isaiah-53": "El siervo sufriente, herido fue por nuestras rebeliones, ",
            "galatians-5": "El fruto del Espíritu, amor gozo paz, ",
            "1-corinthians-15": "La resurrección de los muertos, ¿Dónde está oh muerte tu aguijón?, ",
            "proverbs-3": "Fía de Jehová de todo tu corazón, ",
            "matthew-5": "Las Bienaventuranzas, el Sermón del Monte, ",
            "john-14": "Yo soy el camino la verdad y la vida, ",
            "ephesians-2": "Porque por gracia sois salvos por medio de la fe, ",
            "genesis-1": "La creación del mundo, Dios creó los cielos y la tierra, en el principio, ",
            "genesis-3": "La serpiente en el jardín del Edén, la caída del hombre, el pecado original, ",
            "acts-3": "Pedro sana al cojo, levántate y anda, el mendigo en la puerta del templo, ",
            "matthew-17": "La transfiguración de Jesús, su rostro resplandeció como el sol, Moisés y Elías aparecieron, ",
            "matthew-3": "El bautismo de Jesús, Juan el Bautista bautiza a Jesús en el río Jordán, el Espíritu descendió como paloma, ",
            "joshua-3": "El cruce del río Jordán, el arca del pacto guió al pueblo, la entrada a la tierra prometida, ",
            "romans-8": "Los que predestinó también los llamó, nada nos separará del amor de Dios, ",
            "ephesians-1": "La predestinación, escogidos en Cristo antes de la fundación del mundo, elección y vocación, ",
            "2chronicles-7": "Si se humilla mi pueblo y ora, yo sanaré su tierra, ",
            "matthew-14": "La multiplicación de los panes y los peces, Jesús camina sobre el agua, ",
            "exodus-32": "El becerro de oro, el pueblo hizo un ídolo, ",
            "1-corinthians-13": "El amor es paciente es bondadoso, el capítulo del amor, ",
            "jeremiah-31": "El nuevo pacto, pondré mi ley en sus corazones, ",
            "1-kings-17": "Elías alimentado por los cuervos, la viuda de Sarepta, ",
            "isaiah-41": "No temas porque yo estoy contigo, no desmayes porque yo soy tu Dios, ",
        },
        "verse_anchors": {
            "john-3": "Porque de tal manera amó Dios al mundo que ha dado a su Hijo unigénito; ",
            "psalms-23": "Jehová es mi pastor nada me faltará; aunque ande en valle de sombra de muerte; ",
            "john-14": "Yo soy el camino y la verdad y la vida nadie viene al Padre sino por mí; ",
            "philippians-4": "Por nada estéis afanosos sino sean conocidas vuestras peticiones delante de Dios; todo lo puedo en Cristo que me fortalece; ",
            "ephesians-2": "Porque por gracia sois salvos por medio de la fe y esto no de vosotros pues es don de Dios; ",
            "john-11": "Jesús resucita a Lázaro de entre los muertos; Lázaro ven fuera; Jesús lloró; yo soy la resurrección y la vida; ",
        },
    },
    "de": {
        "canonical_names": {
            "exodus-3": "Der brennende Dornbusch, Mose und der brennende Busch, ",
            "luke-15": "Das Gleichnis vom verlorenen Sohn, der verlorene Sohn, ",
            "luke-10": "Der barmherzige Samariter, das Gleichnis vom barmherzigen Samariter, ",
            "ephesians-6": "Die Waffenrüstung Gottes, ziehet an die Waffenrüstung Gottes, ",
            "john-3": "Denn also hat Gott die Welt geliebt, Nikodemus, ",
            "genesis-6": "Die Arche Noah, die Sintflut, ",
            "genesis-37": "Josefs bunter Mantel, Josef von seinen Brüdern verkauft, ",
            "exodus-14": "Der Durchzug durch das Rote Meer, Mose teilt das Meer, ",
            "exodus-20": "Die Zehn Gebote, das Gesetz am Sinai, ",
            "judges-16": "Simson und Delila, der stärkste Mann, ",
            "daniel-6": "Daniel in der Löwengrube, ",
            "daniel-3": "Der Feuerofen, Schadrach Meschach und Abed-Nego, ",
            "matthew-28": "Die Auferstehung Jesu, das leere Grab, ",
            "john-11": "Die Auferweckung des Lazarus, Lazarus komm heraus, ",
            "acts-9": "Die Bekehrung des Saulus auf dem Weg nach Damaskus, ",
            "revelation-13": "Das Malzeichen des Tieres, die Zahl 666, ",
            "psalms-23": "Der Herr ist mein Hirte mir wird nichts mangeln, ",
            "malachi-3": "Der Zehnte, bringt den Zehnten in mein Haus, ",
            "1-kings-18": "Elia auf dem Berg Karmel, Feuer vom Himmel, ",
            "matthew-26": "Das letzte Abendmahl, Brot und Wein, ",
            "matthew-6": "Das Vaterunser, sorget nicht für den morgigen Tag, ",
            "philippians-4": "Sorget nichts, ich vermag alles durch den der mich mächtig macht, ",
            "isaiah-53": "Der leidende Gottesknecht, um unsrer Missetat willen verwundet, ",
            "galatians-5": "Die Frucht des Geistes, Liebe Freude Friede, ",
            "1-corinthians-15": "Die Auferstehung der Toten, Tod wo ist dein Stachel, ",
            "proverbs-3": "Verlass dich auf den Herrn von ganzem Herzen, ",
            "matthew-5": "Die Bergpredigt, die Seligpreisungen, selig sind die geistlich Armen, ",
            "john-14": "Ich bin der Weg und die Wahrheit und das Leben, ",
            "ephesians-2": "Denn aus Gnade seid ihr gerettet durch Glauben, ",
            "genesis-1": "Die Schöpfung der Welt, Gott schuf Himmel und Erde, am Anfang, ",
            "genesis-3": "Die Schlange im Garten Eden, der Sündenfall, ",
            "acts-3": "Petrus heilt den Lahmen, stehe auf und wandle, der Bettler am Tempel, ",
            "matthew-17": "Die Verklärung Jesu, sein Angesicht leuchtete wie die Sonne, Mose und Elia erschienen, ",
            "matthew-3": "Die Taufe Jesu, Johannes der Täufer tauft Jesus im Jordan, der Geist Gottes kam wie eine Taube, ",
            "joshua-3": "Der Durchzug durch den Jordan, die Bundeslade führte das Volk, der Einzug ins gelobte Land, ",
            "romans-8": "Die er vorherbestimmt hat die hat er auch berufen, nichts kann uns scheiden von der Liebe Gottes, ",
            "ephesians-1": "Die Vorherbestimmung, auserwählt in Christus vor Grundlegung der Welt, ",
            "2chronicles-7": "Wenn mein Volk sich demütigt und betet, will ich ihr Land heilen, ",
            "matthew-14": "Speisung der Fünftausend, Jesus geht auf dem Wasser, ",
            "exodus-32": "Das goldene Kalb, das Volk machte ein Götzenbild, ",
            "1-corinthians-13": "Die Liebe ist geduldig und freundlich, das Hohelied der Liebe, ",
            "jeremiah-31": "Der neue Bund, ich lege mein Gesetz in ihr Herz, ",
            "1-kings-17": "Elia von Raben ernährt, die Witwe von Zarpath, ",
            "isaiah-41": "Fürchte dich nicht denn ich bin mit dir, verzage nicht denn ich bin dein Gott, ",
        },
        "verse_anchors": {
            "john-3": "Denn also hat Gott die Welt geliebt dass er seinen eingeborenen Sohn gab; ",
            "psalms-23": "Der Herr ist mein Hirte mir wird nichts mangeln; und ob ich schon wanderte im finstern Tal; ",
            "john-14": "Ich bin der Weg und die Wahrheit und das Leben niemand kommt zum Vater denn durch mich; ",
            "philippians-4": "Sorget nichts sondern in allen Dingen lasset eure Bitten im Gebet vor Gott kundwerden; ich vermag alles durch den der mich mächtig macht; ",
            "ephesians-2": "Denn aus Gnade seid ihr gerettet durch Glauben und das nicht aus euch Gottes Gabe ist es; ",
        },
    },
}


def load_anchors(lang: str):
    """Load per-language canonical names and verse anchors.
    Falls back to English for any missing entries."""
    if lang == "en":
        return CANONICAL_NAMES, VERSE_ANCHORS
    path = OUTPUT_DIR / "anchors" / f"{lang}.json"
    if path.exists():
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        cn = data.get("canonical_names", {})
        va = data.get("verse_anchors", {})
        merged_cn = dict(CANONICAL_NAMES)
        merged_cn.update(cn)
        merged_va = dict(VERSE_ANCHORS)
        merged_va.update(va)
        print(f"  Loaded translated anchors: {len(cn)} canonical, {len(va)} verse")
    else:
        print(f"  No translated anchors for {lang}, using English fallback")
        merged_cn = dict(CANONICAL_NAMES)
        merged_va = dict(VERSE_ANCHORS)
    patches = NATIVE_PATCHES.get(lang, {})
    cn_patches = patches.get("canonical_names", {})
    va_patches = patches.get("verse_anchors", {})
    cn_appended = 0
    cn_inserted = 0
    for sid, prefix in cn_patches.items():
        if sid in merged_cn:
            merged_cn[sid] = prefix + merged_cn[sid]
            cn_appended += 1
        else:
            merged_cn[sid] = prefix
            cn_inserted += 1
    va_appended = 0
    va_inserted = 0
    for sid, prefix in va_patches.items():
        if sid in merged_va:
            merged_va[sid] = prefix + merged_va[sid]
            va_appended += 1
        else:
            merged_va[sid] = prefix
            va_inserted += 1
    if cn_patches or va_patches:
        print(f"  Native patches: cn={cn_appended} appended + {cn_inserted} inserted, va={va_appended} appended + {va_inserted} inserted")
    return merged_cn, merged_va


def build_corpus_anchors(english_cn: dict, english_va: dict, corpus_entries: list[dict]):
    """Build native canonical names and verse anchors from corpus text.
    Uses professionally translated story titles, takeaways, and bullets
    instead of NLLB machine translations."""
    EMOJI = "\U0001f4dc\U0001f30d⚔️\U0001f4d6\U0001f54a️✨\U0001f525\U0001f451❤️\U0001f3db️\U0001f4a1\U0001f31f⚠️\U0001f30a\U0001f3af\U0001f6e1️⚖️\U0001f4a0\U0001f512\U0001f52e\U0001f310\U0001f54c "

    story_meta = {}
    story_bullets = {}
    for e in corpus_entries:
        sid = e["story_id"]
        if sid not in story_meta:
            title = e.get("story_title", "").lstrip(EMOJI).strip()
            story_meta[sid] = {"title": title, "takeaway": ""}
            story_bullets[sid] = []
        if e["type"] == "takeaway":
            story_meta[sid]["takeaway"] = e["text"]
        elif e["type"] == "bullet":
            story_bullets[sid].append(e["text"])

    native_cn = {}
    cn_count = 0
    for sid, fallback in english_cn.items():
        meta = story_meta.get(sid)
        if not meta or not meta["title"]:
            native_cn[sid] = fallback
            continue
        title = meta["title"]
        tk = meta["takeaway"]
        if tk:
            if len(tk) > 120:
                tk = tk[:120]
            native_cn[sid] = f"{title}, {tk}"
        else:
            native_cn[sid] = title
        cn_count += 1

    native_va = {}
    va_count = 0
    for sid, fallback in english_va.items():
        meta = story_meta.get(sid)
        bullets = story_bullets.get(sid, [])
        if not meta or (not meta["takeaway"] and not bullets):
            native_va[sid] = fallback
            continue
        parts = []
        total = 0
        if meta["takeaway"]:
            parts.append(meta["takeaway"])
            total = len(meta["takeaway"])
        if bullets:
            step = max(1, len(bullets) // 4)
            for i in range(0, len(bullets), step):
                if total > 300:
                    break
                parts.append(bullets[i])
                total += len(bullets[i])
        native_va[sid] = "; ".join(parts)
        va_count += 1

    print(f"  Corpus anchors: {cn_count}/{len(english_cn)} canonical, {va_count}/{len(english_va)} verse built from corpus")
    return native_cn, native_va


def load_corpus(lang: str) -> list[dict]:
    path = OUTPUT_DIR / f"corpus_{lang}.jsonl"
    if not path.exists():
        print(f"  WARNING: {path} not found, skipping")
        return []
    entries = []
    skipped = 0
    crossref_skipped = 0
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                e = json.loads(line)
                if e.get("collection") not in CORE_COLLECTIONS:
                    skipped += 1
                elif e.get("type") == "crossref":
                    crossref_skipped += 1
                else:
                    entries.append(e)
    if skipped:
        print(f"  Filtered out {skipped} apocrypha/deuterocanonical/pseudepigrapha entries")
    if crossref_skipped:
        print(f"  Filtered out {crossref_skipped} crossrefs (duplicated across chapters, pollute ranking)")
    return entries


def build_story_entries(entries: list[dict], canonical_names=None, verse_anchors=None) -> list[dict]:
    if canonical_names is None:
        canonical_names = CANONICAL_NAMES
    if verse_anchors is None:
        verse_anchors = VERSE_ANCHORS

    from collections import OrderedDict
    stories = OrderedDict()
    for e in entries:
        sid = e["story_id"]
        if sid not in stories:
            stories[sid] = {
                "type": "story",
                "collection": e["collection"],
                "book_id": e["book_id"],
                "book_title": e["book_title"],
                "story_id": sid,
                "story_title": e["story_title"],
                "bullet_index": -1,
                "verse_ref": None,
                "refs": e["refs"],
                "lang": e["lang"],
                "bullets": [],
                "takeaway": "",
            }
        if e["type"] == "bullet":
            stories[sid]["bullets"].append(e["text"])
        elif e["type"] == "takeaway":
            stories[sid]["takeaway"] = e["text"]

    story_entries = []
    for sid, s in stories.items():
        canonical = canonical_names.get(sid, "")
        combined = " ".join(s["bullets"])
        if len(combined) > 500:
            combined = combined[:500]
        if s["takeaway"]:
            combined = s["takeaway"] + " " + combined
            if len(combined) > 600:
                combined = combined[:600]
        if canonical:
            combined = canonical + ". " + combined
        s["text"] = combined
        del s["bullets"]
        del s["takeaway"]
        story_entries.append(s)

    for sid, canonical in canonical_names.items():
        if sid not in stories:
            continue
        s = stories[sid]
        story_entries.append({
            "type": "anchor",
            "collection": s["collection"],
            "book_id": s["book_id"],
            "book_title": s["book_title"],
            "story_id": sid,
            "story_title": s["story_title"],
            "bullet_index": -1,
            "verse_ref": None,
            "refs": s["refs"],
            "lang": s["lang"],
            "text": canonical,
        })

    for sid, verse_text in verse_anchors.items():
        if sid not in stories:
            continue
        s = stories[sid]
        story_entries.append({
            "type": "anchor",
            "collection": s["collection"],
            "book_id": s["book_id"],
            "book_title": s["book_title"],
            "story_id": sid,
            "story_title": s["story_title"],
            "bullet_index": -1,
            "verse_ref": None,
            "refs": s["refs"],
            "lang": s["lang"],
            "text": verse_text,
        })

    # EVENT_CLUSTERS anchors are English-only text. Only emit them for the
    # English embedding index — otherwise non-English embeddings leak English
    # event vocabulary and bias semantic search toward English wording.
    # Localized concept search is covered by language-specific CANONICAL_NAMES
    # already merged via load_anchors().
    sample_lang = next(iter(stories.values()))["lang"] if stories else "en"
    if sample_lang == "en":
        for cluster_name, cluster in EVENT_CLUSTERS.items():
            for story_id in cluster["stories"]:
                if story_id not in stories:
                    continue
                s = stories[story_id]
                story_entries.append({
                    "type": "anchor",
                    "collection": s["collection"],
                    "book_id": s["book_id"],
                    "book_title": s["book_title"],
                    "story_id": story_id,
                    "story_title": s["story_title"],
                    "bullet_index": -1,
                    "verse_ref": None,
                    "refs": s["refs"],
                    "lang": s["lang"],
                    "text": cluster["shared_anchor"],
                })

    return story_entries


def prepare_texts(entries: list[dict]) -> list[str]:
    result = []
    for e in entries:
        title = e.get("story_title", "").lstrip("\U0001f4dc\U0001f30d⚔️\U0001f4d6\U0001f54a️✨\U0001f525\U0001f451❤️\U0001f3db️\U0001f4a1\U0001f31f⚠️\U0001f30a\U0001f3af\U0001f6e1️⚖️\U0001f4a0\U0001f512\U0001f52e\U0001f310\U0001f54c ").strip()
        book = e.get("book_title", "")
        text = e["text"].replace("[J]", "").replace("[/J]", "")
        if e["type"] == "anchor":
            result.append(f"passage: {book} - {text}")
        elif e["type"] == "story" and title:
            result.append(f"passage: {book} - {title} (full story): {text}")
        elif e["type"] == "bullet" and title:
            result.append(f"passage: {book} - {title}: {text}")
        elif e["type"] == "takeaway" and title:
            result.append(f"passage: {book} - {title} (summary): {text}")
        else:
            result.append(f"passage: {text}")
    return result


def quantize_int8(vectors):
    vmin = float(vectors.min())
    vmax = float(vectors.max())
    scale = (vmax - vmin) / 254.0
    offset = vmin
    quantized = np.clip(np.round((vectors - offset) / scale - 127), -127, 127).astype(np.int8)
    return quantized, scale, offset


def write_embeddings(quantized, scale, offset, lang):
    n, dim = quantized.shape
    path = OUTPUT_DIR / f"embeddings_{lang}.bin"
    with open(path, "wb") as f:
        f.write(b"BCEF")
        f.write(struct.pack("<I", 1))
        f.write(struct.pack("<I", n))
        f.write(struct.pack("<I", dim))
        f.write(struct.pack("<f", scale))
        f.write(struct.pack("<f", offset))
        f.write(quantized.tobytes())
    return path


def write_metadata(entries, lang):
    meta = []
    for e in entries:
        meta.append({
            "t": e["type"],
            "c": e["collection"],
            "b": e["book_id"],
            "s": e["story_id"],
            "i": e["bullet_index"],
            "v": e["verse_ref"],
            "r": e["refs"],
        })
    path = OUTPUT_DIR / f"metadata_{lang}.json"
    with open(path, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, separators=(",", ":"))
    return path


def write_texts(texts, lang):
    path = OUTPUT_DIR / f"texts_{lang}.json"
    with open(path, "w", encoding="utf-8") as f:
        json.dump(texts, f, ensure_ascii=False)
    return path


def main():
    langs = sys.argv[1:] if len(sys.argv) > 1 else LANGUAGES

    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"Device: {device}", flush=True)
    if device == "cuda":
        print(f"GPU: {torch.cuda.get_device_name(0)}", flush=True)
        print(f"VRAM: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.1f} GB", flush=True)
    print(flush=True)

    finetuned_lora = OUTPUT_DIR / "finetuned_lora"
    finetuned = OUTPUT_DIR / "finetuned_model"
    if finetuned_lora.exists() and (finetuned_lora / "config.json").exists():
        print(f"Loading LoRA fine-tuned model: {finetuned_lora}", flush=True)
        embedder = Embedder(str(finetuned_lora), device=device)
    elif finetuned.exists() and (finetuned / "config.json").exists():
        print(f"Loading fine-tuned model: {finetuned}", flush=True)
        embedder = Embedder(str(finetuned), device=device)
    else:
        print(f"Loading base model: {MODEL_NAME}", flush=True)
        embedder = Embedder(MODEL_NAME, device=device)
    print(f"Model loaded. Embedding dim: {embedder.get_embedding_dimension()}", flush=True)
    print(flush=True)

    for lang in langs:
        print(f"Processing {lang}...", flush=True)
        entries = load_corpus(lang)
        if not entries:
            # Refuse to silently skip — empty corpus means parse_corpus.py
            # never produced data for this language, and we'd otherwise leave
            # stale embedding files in place while reporting "Done".
            print(f"  ERROR: empty corpus for {lang}; rerun parse_corpus.py first.", file=sys.stderr)
            sys.exit(1)

        canonical_names, verse_anchors = load_anchors(lang)
        story_entries = build_story_entries(entries, canonical_names, verse_anchors)
        corpus_sids = {e["story_id"] for e in entries}
        patches = NATIVE_PATCHES.get(lang, {})
        cn_patched = set(patches.get("canonical_names", {}))
        va_patched = set(patches.get("verse_anchors", {}))
        all_patched = cn_patched | va_patched

        # Gate 1: patched story_ids exist in corpus
        not_in_corpus = all_patched - corpus_sids
        if not_in_corpus:
            print(f"  FATAL: {len(not_in_corpus)} patched stories not in corpus: {sorted(not_in_corpus)}")
            sys.exit(1)

        # Gate 2: patched story_ids emit anchor rows
        emitted_anchor_sids = {e["story_id"] for e in story_entries if e["type"] == "anchor"}
        not_emitted = all_patched - emitted_anchor_sids
        if not_emitted:
            print(f"  FATAL: {len(not_emitted)} patches in corpus but no anchor emitted: {sorted(not_emitted)}")
            sys.exit(1)

        # Gate 3: no unknown-* story_ids in corpus
        unknown_sids = [sid for sid in corpus_sids if sid.startswith("unknown")]
        if unknown_sids:
            print(f"  FATAL: {len(unknown_sids)} unknown story_ids: {sorted(unknown_sids)[:10]}")
            sys.exit(1)

        # Gate 4: event cluster story_ids exist in corpus
        cluster_missing = []
        for cname, cluster in EVENT_CLUSTERS.items():
            for sid in cluster["stories"]:
                if sid not in corpus_sids:
                    cluster_missing.append(f"{cname}:{sid}")
        if cluster_missing:
            print(f"  FATAL: {len(cluster_missing)} event cluster stories not in corpus: {cluster_missing[:10]}")
            sys.exit(1)

        # Gate 5: anchor count sanity (baseline ~500-600 for EN-like corpus)
        anchor_count = len([e for e in story_entries if e["type"] == "anchor"])
        MIN_ANCHORS = 400
        if anchor_count < MIN_ANCHORS:
            print(f"  FATAL: only {anchor_count} anchors emitted (expected >= {MIN_ANCHORS})")
            sys.exit(1)
        print(f"  Anchors emitted: {anchor_count}", flush=True)

        entries = entries + story_entries
        print(f"  Added {len(story_entries)} story-level entries", flush=True)

        # Gate 6: patch text appears in prepared texts
        texts = prepare_texts(entries)
        all_text = " ".join(texts)
        cn_patches_dict = patches.get("canonical_names", {})
        va_patches_dict = patches.get("verse_anchors", {})
        missing_text = []
        for sid, patch_text in cn_patches_dict.items():
            first_phrase = patch_text.split(",")[0].strip()
            if first_phrase and first_phrase not in all_text:
                missing_text.append(f"cn:{sid}={first_phrase[:40]}")
        for sid, patch_text in va_patches_dict.items():
            first_phrase = patch_text.split(";")[0].strip()
            if first_phrase and first_phrase not in all_text:
                missing_text.append(f"va:{sid}={first_phrase[:40]}")
        if missing_text:
            print(f"  FATAL: patch text not found in output: {missing_text[:5]}")
            sys.exit(1)
        print(f"  {len(texts)} texts to embed", flush=True)

        t0 = time.time()
        vectors = embedder.encode(
            texts,
            batch_size=BATCH_SIZE,
            normalize=True,
            show_progress=True,
        )
        elapsed = time.time() - t0
        print(f"  Embedded in {elapsed:.1f}s ({len(texts)/elapsed:.0f} texts/sec)", flush=True)

        quantized, scale, offset = quantize_int8(vectors)
        emb_path = write_embeddings(quantized, scale, offset, lang)
        meta_path = write_metadata(entries, lang)
        texts_path = write_texts(texts, lang)

        emb_size = emb_path.stat().st_size / 1024 / 1024
        meta_size = meta_path.stat().st_size / 1024 / 1024
        texts_size = texts_path.stat().st_size / 1024 / 1024
        print(f"  -> {emb_path.name} ({emb_size:.1f} MB)", flush=True)
        print(f"  -> {meta_path.name} ({meta_size:.1f} MB)", flush=True)
        print(f"  -> {texts_path.name} ({texts_size:.1f} MB, for lexical reranking)", flush=True)
        print(flush=True)

    print("Done.", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        import traceback
        print(f"\nERROR: {e}", flush=True)
        traceback.print_exc()
        sys.exit(1)
