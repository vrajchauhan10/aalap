# Aalap 4.2 recommendation engine: test report

Tested 24 Oct 2026 on the Samsung (your real account, new install) and in the simulation tests.

## On your phone: 14 seed songs, 10 songs after each

Same feel = the song has the seed's main feel (from the new feel detector, used for both columns).
Energy gap = how far its tempo is from the seed's (0 = same).

| Seed (genre) | Feel | Same feel: before → after | Energy gap: before → after | Liked songs: before → after |
|---|---|---|---|---|
| Naah — Punjabi pop | Party | 1/10 → **6/10** | 0.26 → **0.06** | 5 → **0** |
| Millionaire — Punjabi/Hindi rap | Energetic | 0/10 → **7/10** | 0.39 → **0.07** | 5 → **1** |
| Kala Chashma — Bollywood party | Party | 2/10 → **10/10** | 0.20 → **0.02** | 3 → **0** |
| Raataan Lambiyan — Romantic | Romantic | 9/10 → **10/10** | 0.10 → **0.06** | 7 → **3** |
| Channa Mereya (From "Ae Dil  — Sad | Romantic | 9/10 → **9/10** | 0.08 → **0.07** | 6 → **3** |
| Kun Faaya Kun — Sufi | Romantic | 9/10 → **10/10** | 0.05 → **0.03** | 5 → **3** |
| Shree Hanuman Chalisa — Devotional | Devotional | 2/10 → **10/10** | 0.11 → **0.01** | 3 → **0** |
| Kho Gaye Hum Kahan — Chill indie | Romantic | 7/10 → **9/10** | 0.09 → **0.06** | 6 → **3** |
| Mere Gully Mein — Hindi hip-hop | Party | 2/10 → **7/10** | 0.13 → **0.05** | 2 → **1** |
| Blinding Lights — English pop | Energetic | 1/10 → **7/10** | 0.27 → **0.05** | 2 → **2** |
| Believer — English rock | Energetic | 4/10 → **9/10** | 0.26 → **0.08** | 2 → **1** |
| Chogada (From "Loveyatri") — Garba | Party | 2/10 → **9/10** | 0.24 → **0.06** | 3 → **0** |
| Naatu Naatu — Telugu | Party | 1/10 → **9/10** | 0.27 → **0.13** | 0 → **0** |
| Interstellar Main Theme — Instrumental | Focus | 4/10 → **7/10** | 0.06 → **0.06** | 0 → **0** |

**All 14: same feel 37% → 85%, liked songs 35% → 12%**

### Millionaire (Yo Yo Honey Singh)

| Before | After |
|---|---|
| 1. Akhiyaan Gulaab — Mitraz (Romantic) | 1. Sau Tarah Ke — Jonita Gandhi (Energetic) |
| 2. Pehle Bhi Main — Vishal Mishra (Romantic) | 2. Blue Eyes — Yo Yo Honey Singh (Energetic) |
| 3. Chaleya — Anirudh Ravichander (Party) | 3. High Rated Gabru (From "High Rated Gabru") — Guru Randhawa (Party) |
| 4. Duniyaa (From "Luka Chuppi") — Akhil (Romantic) | 4. Locked out of Heaven — Bruno Mars (Energetic) |
| 5. Kaise Hua (From "Kabir Singh") — Vishal Mishra (Romantic) | 5. Stressed Out — Twenty One Pilots (Energetic) |
| 6. Tera Hone Laga Hoon — Pritam Chakraborty (Romantic) | 6. Reflections — The Neighbourhood (Energetic) |
| 7. Kya Mujhe Pyar Hai — K.K. (Romantic) | 7. Lutt Le Gaya — Shashwat Sachdev (Energetic) |
| 8. Dil Ibaadat — Kk (Romantic) | 8. Aaj Ki Raat - New Remix — Dj Anupam Tiwari (Party) |
| 9. Tum Se Hi — Pritam (Romantic) | 9. Desi Kalakaar — Honey Singh (Energetic) |
| 10. Mann Mera — Gajendra Verma (Romantic) | 10. Bom Diggy Diggy — Zack Knight (Party) |

### Shree Hanuman Chalisa

| Before | After |
|---|---|
| 1. Jai Shri Ram (Sonu Nigam Version) — Sonu Nigam (Devotional) | 1. Namo Namo — Amit Trivedi (Devotional) |
| 2. Namo Namo — Amit Trivedi (Devotional) | 2. Shri Krishna Govind Hare Murari — Jubin Nautiyal (Devotional) |
| 3. Ishq Hai — Anurag Saikia (Romantic) | 3. Shyama Aan Baso — Parampara Tandon (Devotional) |
| 4. Tu Hain Toh - Neeti Mohan Version (From "Mr. And Mrs. Mahi") — Neeti Mohan (Romantic) | 4. Sri Krishna Govind Hare Murari — Armonian (Devotional) |
| 5. Satranga — Arijit Singh (Romantic) | 5. Om Namo Bhagavate Vasudevaya — Sanjith Hegde (Devotional) |
| 6. Saiyaara (Movie: Saiyaara) — Faheem Abdullah (Romantic) | 6. Krishnaya Vasudevaya - Shri Krishna Mantra — Siddharth Amit Bhavsar (Devotional) |
| 7. Sanam Re — Mithoon (Romantic) | 7. Hanuman Chalisa by Shankar Mahadevan — Shankar Mahadevan (Devotional) |
| 8. Tere Liye — Atif Aslam (Romantic) | 8. Sankatmochan Hanuman Ashtak — Hariharan (Devotional) |
| 9. Pani Da Rang - Male — Ayushmann Khurrana (Romantic) | 9. Shiv Kailasho Ke Vasi — Hansraj Raghuwanshi (Devotional) |
| 10. AMBARSARIYA — SONA MOHAPATRA (Happy) | 10. Shendur Laal Chadhayo (Aarti) — Ravindra Sathe (Devotional) |

### Chogada (garba)

| Before | After |
|---|---|
| 1. What Jhumka ? (From "Rocky Aur Rani Kii Prem Kahaani") — Pritam (Party) | 1. Nashe Si Chadh Gayi — Vishal & Shekhar (Party) |
| 2. Aaj Ki Raat - New Remix — Dj Anupam Tiwari (Party) | 2. Dil Chori (From "Sonu Ke Titu Ki Sweety") — Yo Yo Honey Singh (Party) |
| 3. Dekhte Dekhte — Atif Aslam (Romantic) | 3. Chaleya — Anirudh Ravichander (Party) |
| 4. Dil Ibaadat — Kk (Romantic) | 4. Taras — Amitabh Bhattacharya (Party) |
| 5. Tum Se Hi — Pritam (Romantic) | 5. Kamariya — Lijo George (Party) |
| 6. Saibo — Sachin-Jigar (Romantic) | 6. Nagada Sang Dhol — Sanjay Leela Bhansali (Party) |
| 7. Mast Magan (From "2 States) — Arijit Singh (Romantic) | 7. What Jhumka ? (From "Rocky Aur Rani Kii Prem Kahaani") — Pritam (Party) |
| 8. Mitwa — Shankar-Ehsaan-Loy (Romantic) | 8. Baby Doll — Meet Bros Anjjan (Party) |
| 9. Main Agar Kahoon — Vishal-Shekhar (Happy) | 9. Aaj Raat Ka Scene — Badshah (Romantic) |
| 10. Hale Dil — Harshit Saxena (Romantic) | 10. Chikni Chameli — Ajay-Atul (Party) |

## Tapping a song in Liked Songs (on your phone)

- **Tum Hi Ho**: before, the next 10 songs were the rest of Liked Songs in order. Now 3 of 10 are liked songs (at random, ones that fit), the rest are new songs with the same feel.
- **Kesariya**: before, the next 10 songs were the rest of Liked Songs in order. Now 3 of 10 are liked songs (at random, ones that fit), the rest are new songs with the same feel.

## Simulation (app/build/reports/reco-scenarios.md)

## Tapping a song in Liked Songs

Old: the next 10 songs were the rest of Liked Songs, in order (100% from the list).
New:  36% from Liked Songs (picked at random among the ones that fit), the rest recommendations;  98% share the tapped song's feel.

## Skipping steers the queue

Sad songs after a sad start:  94% normally,  77% after you skipped two sad songs.

## Every genre: 10 songs after a seed (60 runs each)

Same feel = the pick has the seed's main feel. Energy gap = how far its tempo is from the seed's (0 = same).

| Seed genre | Same feel (old → new) | Energy gap (old → new) | Same language (old → new) | Liked songs (old → new) | Same artist twice in a row (old → new) |
|---|---|---|---|---|---|
| Punjabi party |   8% → ** 96%** | 0.48 → **0.02** |   8% → ** 95%** |  82% → **  4%** | 0 → **0** |
| Punjabi romantic |  99% → **100%** | 0.01 → **0.00** |   8% → ** 50%** |  84% → ** 32%** | 0 → **0** |
| Punjabi rap |   5% → ** 90%** | 0.52 → **0.05** |   5% → ** 90%** |  84% → ** 10%** | 0 → **0** |
| Bollywood romantic | 100% → ** 99%** | 0.00 → **0.00** | 100% → **100%** |  87% → ** 39%** | 0 → **0** |
| Bollywood sad | 100% → **100%** | 0.13 → **0.00** | 100% → **100%** |  89% → ** 31%** | 0 → **0** |
| Bollywood party |   5% → ** 88%** | 0.44 → **0.05** | 100% → **100%** |  81% → ** 11%** | 0 → **0** |
| Sufi |   7% → ** 92%** | 0.02 → **0.00** | 100% → **100%** |  84% → ** 18%** | 0 → **0** |
| Devotional / bhajan |   4% → ** 89%** | 0.06 → **0.01** | 100% → **100%** |  86% → ** 12%** | 0 → **0** |
| Lofi / chill indie |   7% → ** 89%** | 0.06 → **0.01** | 100% → **100%** |  83% → ** 10%** | 0 → **0** |
| Hindi hip-hop |   4% → ** 91%** | 0.52 → **0.05** | 100% → **100%** |  85% → **  9%** | 0 → **0** |
| English pop |   7% → ** 94%** | 0.23 → **0.01** |   7% → ** 95%** |  81% → **  5%** | 0 → **0** |
| English rock / workout |   4% → ** 92%** | 0.47 → **0.03** |   6% → ** 94%** |  81% → **  5%** | 0 → **0** |
| Gujarati garba |   9% → ** 97%** | 0.35 → **0.01** |   9% → ** 97%** |  80% → **  3%** | 0 → **0** |
| Telugu mass |   7% → ** 92%** | 0.50 → **0.04** |   7% → ** 90%** |  82% → **  8%** | 0 → **0** |
| Instrumental / focus |   6% → ** 91%** | 0.16 → **0.02** |   6% → ** 91%** |  83% → **  9%** | 0 → **0** |

**Average same feel: old  25% → new  93%**

## A brand-new listener (no history)

| Seed genre | Same feel | Same language |
|---|---|---|
| Punjabi party |  99% |  97% |
| Sufi |  96% | 100% |
| Devotional / bhajan |  98% | 100% |
| Lofi / chill indie |  96% | 100% |
| English rock / workout |  95% |  98% |
| Gujarati garba | 100% | 100% |

## The mix: songs you know, new songs, trending

Across all genres: songs you already play  74% →  16%, liked songs  84% →  14%, trending   7% →  12%.

## Loop modes

Tested on the phone (button, badge, menu) and in LoopModeTest (what plays after each song in every mode):

| Mode | After the last song |
|---|---|
| Off | recommendations carry on |
| This song once more | the song again, then the queue carries on |
| This song, on repeat | the song forever |
| This playlist once more | the queue again from the top, then recommendations |
| This playlist, on repeat | the queue from the top, forever (nothing is added) |

Tests: 36 unit tests, all passing (QueueScenarioTest, VibeLexiconTest, LoopModeTest, FusionTest and the rest).
