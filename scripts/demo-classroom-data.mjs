// Original, fictional content for the optional local classroom demo.
const choice = (prompt, correct, alternatives, explanation) => ({
  type: 'SINGLE_CHOICE', prompt, content: { options: [correct, ...alternatives].map((text, i) => ({ id: `o${i}`, text })) },
  answerKey: { correctOptionId: 'o0' }, explanation,
});
const fill = (prompt, before, value, after, explanation) => ({
  type: 'FILL_BLANK', prompt, content: { parts: [{ kind: 'TEXT', text: before }, { kind: 'BLANK', blankId: 'b1' }, { kind: 'TEXT', text: after }] },
  answerKey: { blanks: [{ blankId: 'b1', acceptedAnswers: [value] }], caseSensitive: false, normalizeWhitespace: true }, explanation,
});
const matching = (prompt, pairs) => ({
  type: 'MATCHING', prompt,
  content: { leftItems: pairs.map(([text], i) => ({ id: `l${i}`, text })), rightItems: pairs.map(([, text], i) => ({ id: `r${i}`, text })) },
  answerKey: { pairs: pairs.map((_, i) => ({ leftId: `l${i}`, rightId: `r${i}` })) },
  explanation: pairs.map(([a, b]) => `${a}: ${b}`).join('; '),
});
const ordering = (prompt, words) => ({
  type: 'ORDERING', prompt, content: { tokens: words.map((text, i) => ({ id: `t${i}`, text })) },
  answerKey: { acceptedOrders: [words.map((_, i) => `t${i}`)] }, explanation: words.join(' ') + '.',
});

export const source = { label: 'Học liệu tự biên soạn cho lớp demo giả lập', url: null, license: null };
export const units = [
  { code: 'demo-school-clubs-v1', title: '[Demo] School & clubs', description: 'Đọc về trường học, thư viện và câu lạc bộ; luyện hiện tại đơn.', lessons: [
    { title: 'A morning at Green School',
      text: 'Mai is twelve years old. She studies at Green School. Her first lesson starts at eight o’clock. She goes to school by bus with her brother. Her favourite subject is English. At break time, Mai reads stories in the library. The library is next to the music room. She has lunch at school at twelve. After lunch, she plays chess with her friend Ben. School finishes at four in the afternoon.',
      questions: [
        choice('What time does Mai’s first lesson start?', 'At eight.', ['At seven.', 'At nine.'], 'The passage says her first lesson starts at eight.'),
        fill('Complete the sentence about Mai’s journey.', 'Mai goes to school by ', 'bus', '.', 'Mai travels by bus with her brother.'),
        matching('Match each school place with its activity.', [['library', 'read stories'], ['music room', 'play music'], ['classroom', 'have lessons']]),
        choice('Where is the library at Green School?', 'Next to the music room.', ['Behind the supermarket.', 'Opposite the hospital.'], 'The library is next to the music room.'),
        fill('Complete the sentence about lunch.', 'Mai has lunch at ', 'twelve', '.', 'The passage gives the lunch time as twelve.'),
        ordering('Arrange the words to describe Mai’s favourite subject.', ['Her', 'favourite', 'subject', 'is', 'English']),
        choice('Who plays chess with Mai?', 'Ben.', ['Her teacher.', 'Her mother.'], 'After lunch, Mai plays chess with her friend Ben.'),
        matching('Match the times with Mai’s activities.', [['eight', 'first lesson'], ['twelve', 'lunch'], ['four', 'school finishes']]),
        ordering('Arrange the words to describe Mai at break time.', ['Mai', 'reads', 'stories', 'in', 'the', 'library']),
      ] },
    { title: 'Our Friday science club',
      text: 'Ben joins the science club every Friday. The club meets at three in Room Ten. There are six students and one teacher. The teacher is Ms Rose. Today the students are growing beans in small pots. Each pot needs water and sunlight. Ben puts his pot near the window. His friend Lucy writes the date in a notebook. They check the plants every Monday. The club finishes at four, and everyone cleans the tables before going home.',
      questions: [
        choice('When does Ben’s science club meet?', 'Every Friday.', ['Every Tuesday.', 'Every Sunday.'], 'Ben joins the club every Friday.'),
        fill('Complete the sentence about the meeting room.', 'The club meets in Room ', 'Ten', '.', 'The club meets in Room Ten.'),
        matching('Match the science words with their meanings.', [['sunlight', 'light from the sun'], ['notebook', 'a book for writing notes'], ['pot', 'a container for a plant']]),
        choice('Why does Ben put his pot near the window?', 'The plant needs sunlight.', ['The pot needs music.', 'The notebook is there.'], 'The passage says the plants need water and sunlight.'),
        fill('Complete the sentence about the teacher.', 'The teacher is Ms ', 'Rose', '.', 'Ms Rose teaches the club.'),
        ordering('Arrange the words to describe Lucy.', ['Lucy', 'writes', 'the', 'date', 'in', 'a', 'notebook']),
        choice('What do the students do before going home?', 'Clean the tables.', ['Buy new windows.', 'Play football.'], 'Everyone cleans the tables before going home.'),
        matching('Match the people and groups with their actions.', [['Ben', 'puts a pot near the window'], ['Lucy', 'writes the date'], ['everyone', 'cleans the tables']]),
        ordering('Arrange the words about checking the plants.', ['They', 'check', 'the', 'plants', 'every', 'Monday']),
      ] },
  ] },
  { code: 'demo-health-sports-v1', title: '[Demo] Healthy life & sports', description: 'Đọc về thói quen lành mạnh và thể thao; luyện từ vựng và sắp xếp câu.', lessons: [
    { title: 'Anna’s healthy Saturday',
      text: 'Anna gets up at seven on Saturday. She drinks a glass of water and eats bread and eggs for breakfast. At nine, she walks to the park with her father. They walk for thirty minutes. Anna takes a bottle of water because the weather is warm. For lunch, she eats rice, fish and vegetables. In the afternoon, she reads a book at home. She does not use her phone before bed. She goes to bed at ten.',
      questions: [
        choice('What does Anna drink after getting up?', 'A glass of water.', ['A cup of coffee.', 'A glass of juice.'], 'Anna drinks a glass of water after getting up.'),
        fill('Complete the sentence about Anna’s walk.', 'Anna walks to the ', 'park', '.', 'Anna and her father walk to the park.'),
        matching('Match the foods with their groups.', [['fish', 'protein'], ['rice', 'grains'], ['carrots', 'vegetables']]),
        choice('Why does Anna take water to the park?', 'The weather is warm.', ['The park is closed.', 'She wants to water a tree.'], 'The passage says she takes water because the weather is warm.'),
        fill('Complete the sentence about the length of the walk.', 'They walk for ', 'thirty', ' minutes.', 'Anna and her father walk for thirty minutes.'),
        ordering('Arrange the words about Anna’s afternoon.', ['She', 'reads', 'a', 'book', 'at', 'home']),
        choice('What time does Anna go to bed?', 'At ten.', ['At eight.', 'At midnight.'], 'Anna goes to bed at ten.'),
        matching('Match the times with Anna’s activities.', [['seven', 'gets up'], ['nine', 'walks to the park'], ['ten', 'goes to bed']]),
        ordering('Arrange the words about Anna’s bedtime habit.', ['She', 'does', 'not', 'use', 'her', 'phone', 'before', 'bed']),
      ] },
    { title: 'An afternoon at the sports centre',
      text: 'Tom and Nina visit the sports centre on Sunday. Tom likes swimming, but Nina prefers badminton. The swimming pool opens at two. Nina’s badminton class starts at half past two. Their coach is Mr Lee. He asks everyone to warm up before exercise. Tom brings a towel and Nina brings a racket. They both bring water. After their activities, they meet at the entrance at four. They take the bus home together.',
      questions: [
        choice('Which sport does Nina prefer?', 'Badminton.', ['Swimming.', 'Basketball.'], 'Nina prefers badminton.'),
        fill('Complete the sentence about the coach.', 'Their coach is Mr ', 'Lee', '.', 'The coach is Mr Lee.'),
        matching('Match the sports equipment with its use.', [['racket', 'play badminton'], ['towel', 'dry yourself'], ['water bottle', 'carry a drink']]),
        choice('What does Mr Lee ask everyone to do before exercise?', 'Warm up.', ['Go home.', 'Eat a large meal.'], 'Mr Lee asks everyone to warm up before exercise.'),
        fill('Complete the sentence about the meeting place.', 'They meet at the ', 'entrance', '.', 'Tom and Nina meet at the entrance at four.'),
        ordering('Arrange the words about the pool.', ['The', 'swimming', 'pool', 'opens', 'at', 'two']),
        choice('How do Tom and Nina travel home?', 'By bus.', ['By train.', 'By boat.'], 'They take the bus home together.'),
        matching('Match each person with the item they bring.', [['Tom', 'a towel'], ['Nina', 'a racket'], ['both friends', 'water']]),
        ordering('Arrange the words about the friends going home.', ['They', 'take', 'the', 'bus', 'home', 'together']),
      ] },
  ] },
];

export const students = [
  { email: 'demo.minhanh@english.local', name: '[Demo] Minh Anh', lessons: 10, wrongEvery: 0 },
  { email: 'demo.hoangnam@english.local', name: '[Demo] Hoàng Nam', lessons: 9, wrongEvery: 7 },
  { email: 'demo.thuha@english.local', name: '[Demo] Thu Hà', lessons: 8, wrongEvery: 4 },
  { email: 'demo.giabao@english.local', name: '[Demo] Gia Bảo', lessons: 6, wrongEvery: 3 },
  { email: 'demo.lananh@english.local', name: '[Demo] Lan Anh', lessons: 4, wrongEvery: 2 },
  { email: 'demo.ducminh@english.local', name: '[Demo] Đức Minh', lessons: 2, wrongEvery: 2 },
];
