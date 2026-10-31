// Original sample curriculum, shared by the opt-in seed and isolated browser tests.
export const sampleVersion = 'activities-1.1.0';
export const source = { label: 'English Learning — nội dung tự biên soạn', url: null, license: 'Dùng cho học tập và kiểm thử dự án' };
const choice = (key, prompt, options, correct, explanation, materialKey = null) => ({ key, type: 'SINGLE_CHOICE', prompt, materialKey, content: { options: options.map((text, i) => ({ id: 'o' + i, text })) }, answerKey: { correctOptionId: 'o' + correct }, explanation });
const fill = (key, prompt, before, answer, after = '', materialKey = null) => ({ key, type: 'FILL_BLANK', prompt, materialKey, content: { parts: [{ kind: 'TEXT', text: before }, { kind: 'BLANK', blankId: 'word' }, { kind: 'TEXT', text: after }] }, answerKey: { blanks: [{ blankId: 'word', acceptedAnswers: [answer] }], caseSensitive: false, normalizeWhitespace: true }, explanation: `Từ phù hợp với ngữ cảnh câu này là “${answer}”.` });
const matching = (key, prompt, pairs) => ({ key, type: 'MATCHING', prompt, materialKey: null, content: { leftItems: pairs.map(([text], i) => ({ id: 'l' + i, text })), rightItems: pairs.map(([, text], i) => ({ id: 'r' + i, text })) }, answerKey: { pairs: pairs.map((_, i) => ({ leftId: 'l' + i, rightId: 'r' + i })) }, explanation: pairs.map(([a, b]) => `${a}: ${b}`).join('; ') });
const ordering = (key, prompt, words) => ({ key, type: 'ORDERING', prompt, materialKey: null, content: { tokens: words.map((text, i) => ({ id: 't' + i, text })) }, answerKey: { acceptedOrders: [words.map((_, i) => 't' + i)] }, explanation: words.join(' ') });

const clozeExample = ['On Monday, Ben carries an ', { questionKey: 'cloze-guide', blankId: 'word' }, ' because the weather report says it will rain. He stays dry on his walk to school.'];
const clozeMain = [
  'Last Saturday, Mai and her friends planned a day in Green Town. They wanted to visit new places without spending much money. At eight o’clock, they met at the station and took the ', { questionKey: 'cloze-bus', blankId: 'word' }, ' to the town centre. The driver reminded everyone to keep their tickets until the end of the journey.\n\n',
  'Their first stop was the public ', { questionKey: 'cloze-library', blankId: 'word' }, ', where people could borrow books for free. A volunteer showed them a display about local birds. Mai wrote down the opening hours so she could return with her younger brother. The building was quiet, and the group spoke softly while other visitors were reading.\n\n',
  'At noon, the friends walked to the park beside the river. They had brought sandwiches, fruit and water from home, so they enjoyed a ', 'picnic', ' on the grass instead of eating at a restaurant. Before leaving, they collected all their rubbish. Mai decided that a simple day with friends could be both interesting and affordable.'
];
const textOf = parts => parts.map(p => typeof p === 'string' ? p : '____').join('');
export const sampleMaterials = [
  { key: 'cloze-example', title: 'Ví dụ điền từ — A rainy morning', readingText: textOf(clozeExample), parts: clozeExample },
  { key: 'cloze-main', title: 'Đọc điền từ — A day in Green Town', readingText: textOf(clozeMain), parts: clozeMain },
  { key: 'reading-example', title: 'Ví dụ đọc hiểu — An afternoon class', readingText: 'Tom usually studies at home after school. On Wednesday, he goes to the community centre for an art class. The class starts at four and finishes at five. Tom enjoys drawing animals with his new friends.' },
  { key: 'reading-main', title: 'Đọc hiểu — The neighbourhood garden', readingText: 'A small piece of land behind Anna’s apartment building used to be empty. Last spring, several neighbours decided to turn it into a shared garden. They wanted a place where families could spend time outside and learn to grow food. Anna joined the project even though she had never planted anything before.\n\nEvery Sunday morning, the neighbours meet for two hours. Some people water the plants while others collect dry leaves or repair the wooden boxes. Children have their own small area for growing beans. Mr Long, a retired teacher, explains how to look after the plants. He asks everyone to bring a reusable bottle because there is drinking water at the entrance.\n\nThe garden does not sell its vegetables. When food is ready, the group shares it among the families who help with the work. Anna thinks the best result is not the fresh food. Before the project, she knew very few people in her building. Now she can ask her neighbours for advice and often stops to talk with them. The group hopes to add a reading bench next year, but first they need to save enough money for the wood.' }
];
export const sampleQuestions = [
  choice('choice-guide', 'Chọn lời chào phù hợp vào buổi sáng.', ['Good morning!', 'Good night!', 'Goodbye!', 'See you tomorrow!'], 0, 'Good morning là lời chào vào buổi sáng.'),
  choice('choice-time', 'The train leaves at 8:00. It is 7:45 now. How long do you have?', ['Fifteen minutes', 'Thirty minutes', 'One hour', 'Two hours'], 0, 'Từ 7:45 đến 8:00 là mười lăm phút.'),
  choice('choice-place', 'Where can you buy fresh bread?', ['At a bakery', 'At a station', 'At a museum', 'At a hospital'], 0, 'Bakery là cửa hàng bánh mì.'),
  matching('matching-guide', 'Ghép từ với nghĩa để làm quen.', [['morning', 'buổi sáng'], ['evening', 'buổi tối']]),
  matching('matching-main', 'Ghép địa điểm với hoạt động phù hợp.', [['library', 'borrow a book'], ['park', 'walk outside'], ['station', 'catch a train']]),
  ordering('ordering-guide', 'Sắp xếp thành lời giới thiệu.', ['My', 'name', 'is', 'Linh.']),
  ordering('ordering-main', 'Sắp xếp thành câu kể về hoạt động cuối tuần.', ['We', 'visit', 'our', 'grandparents', 'on', 'Sundays.']),
  fill('fill-guide', 'Điền động từ để hoàn thành câu.', 'I ', 'am', ' a student.'),
  fill('fill-days', 'Điền ngày đứng sau Monday.', 'Monday, ', 'Tuesday', ', Wednesday.'),
  fill('fill-verb', 'Điền dạng đúng của go.', 'She ', 'goes', ' to school by bike every day.'),
  fill('cloze-guide', 'Ví dụ: Ben mang vật gì để tránh mưa?', 'Ben carries an ', 'umbrella', '.', 'cloze-example'),
  fill('cloze-bus', 'Điền phương tiện Mai đi từ nhà ga vào trung tâm.', 'Phương tiện: ', 'bus', '', 'cloze-main'),
  fill('cloze-library', 'Điền tên địa điểm có thể mượn sách miễn phí.', 'Địa điểm: ', 'library', '', 'cloze-main'),
  choice('reading-guide', 'Ví dụ: Where does Tom take his art class?', ['At the community centre', 'At home', 'At the library', 'At school'], 0, 'Đoạn ví dụ nói Tom đến community centre vào thứ Tư.', 'reading-example'),
  choice('reading-purpose', 'Why did the neighbours start the garden?', ['To spend time outside and learn to grow food', 'To sell vegetables to shops', 'To build new apartments', 'To open a restaurant'], 0, 'Mục tiêu ở đoạn đầu là có nơi ở ngoài trời và học trồng thực phẩm.', 'reading-main'),
  choice('reading-schedule', 'When does the group work together?', ['Every Sunday morning', 'Every Friday evening', 'Only in summer', 'Every afternoon'], 0, 'Đoạn hai nêu lịch gặp là mỗi sáng Chủ nhật.', 'reading-main'),
  choice('listening-guide', 'Nghe và chọn đáp án: What time does Linh get up?', ['At seven', 'At eight', 'At nine', 'At ten'], 0, 'Audio nói Linh thức dậy lúc bảy giờ.', 'listening-main'),
  choice('listening-main', 'Nghe và chọn đáp án: How does Linh go to school?', ['By bike', 'By bus', 'On foot', 'By train'], 1, 'Audio nói Linh đi xe buýt đến trường.', 'listening-main'),
  choice('review-choice', 'Choose the place where you can see historical objects.', ['A museum', 'A bakery', 'A playground', 'A pharmacy'], 0, 'Museum là bảo tàng.'),
  fill('review-fill', 'Complete the sentence with have in the correct form.', 'He ', 'has', ' a small garden.'),
  matching('review-matching', 'Ghép phương tiện với nơi đón.', [['train', 'railway station'], ['plane', 'airport']]),
  ordering('review-ordering', 'Sắp xếp câu về thói quen đọc sách.', ['I', 'read', 'books', 'every', 'night.'])
];
const groups = [
  ['CHOICE', ['choice-guide', 'choice-time', 'choice-place']],
  ['MATCHING', ['matching-guide', 'matching-main']],
  ['ORDERING', ['ordering-guide', 'ordering-main']],
  ['FILL', ['fill-guide', 'fill-days', 'fill-verb']],
  ['READING_CLOZE', ['cloze-guide', 'cloze-bus', 'cloze-library']],
  ['READING_CHOICE', ['reading-guide', 'reading-purpose', 'reading-schedule']],
  ['LISTENING_CHOICE', ['listening-guide', 'listening-main']]
];

// client.action must durably retain requestId + exact payload before a request can be sent.
export async function provisionActivitySample(client, namespace) {
  const topic = await client.action('topic', '/api/author/topics', { code: namespace, name: 'Everyday adventures — 18 câu', description: 'Bài mẫu bảy hoạt động, kết thúc bằng nghe chọn đáp án.' });
  const objective = await client.action('objective', '/api/author/objectives', { topicId: topic.id, code: namespace + '-skills', name: 'Hiểu và sử dụng tiếng Anh trong sinh hoạt', description: 'Từ vựng, cấu trúc câu, đọc điền từ và đọc hiểu.' });
  const unit = await client.action('unit', '/api/author/units', { topicId: topic.id, title: 'Everyday adventures', description: 'Một bài mẫu 18 câu, ví dụ riêng cho hai dạng đọc.', stage: 1, sortOrder: 0 });
  const materials = new Map(), questions = new Map();
  for (const spec of sampleMaterials) {
    const root = await client.action('material-' + spec.key, '/api/author/materials', { kind: 'READING', title: spec.title, readingText: spec.readingText, assetId: null, altText: null, source });
    materials.set(spec.key, root.draftVersionId);
  }
  const audio = await client.upload('listening-audio', 'daily-routine.wav');
  const listening = await client.action('material-listening', '/api/author/materials', { kind: 'AUDIO', title: 'Nghe — Linh’s daily routine', assetId: audio.id, transcript: 'Every morning, Linh gets up at seven. She eats breakfast at home and takes the bus to school. In the evening, she reads a book and goes to bed at ten.', startSeconds: 0, endSeconds: null, source: { label: 'Nội dung dự án; giọng tổng hợp Microsoft Zira Desktop', url: null, license: 'Audio demo tạo trên máy phát triển' } });
  materials.set('listening-main', listening.draftVersionId);
  for (const spec of sampleQuestions) {
    const { key, materialKey, ...data } = spec;
    const root = await client.action('question-' + key, '/api/author/questions', { ...data, topicId: topic.id, objectiveId: objective.id, difficulty: 'EASY', hint: key.endsWith('-guide') ? 'Đọc ngữ cảnh và xác định ý nghĩa hoặc cấu trúc câu trước khi chọn đáp án.' : null, materialVersionId: materialKey ? materials.get(materialKey) : null, source });
    questions.set(key, root.draftVersionId);
  }
  const activities = groups.map(([kind, keys]) => ({ id: kind.toLowerCase(), kind, questionVersionIds: keys.map(k => questions.get(k)), passages: kind === 'READING_CLOZE' ? sampleMaterials.filter(m => m.parts).map(m => ({ materialVersionId: materials.get(m.key), parts: m.parts.map(p => typeof p === 'string' ? { kind: 'TEXT', text: p } : { kind: 'BLANK', questionVersionId: questions.get(p.questionKey), blankId: p.blankId }) })) : [] }));
  const positions = { GUIDED: 0, INDEPENDENT: 0, REVIEW_VARIANT: 0 };
  const slots = groups.flatMap(([, keys]) => keys.map((key, i) => ({ questionVersionId: questions.get(key), practiceRole: i === 0 ? 'GUIDED' : 'INDEPENDENT' }))).concat(sampleQuestions.filter(q => q.key.startsWith('review-')).map(q => ({ questionVersionId: questions.get(q.key), practiceRole: 'REVIEW_VARIANT' }))).map(s => ({ ...s, position: positions[s.practiceRole]++ }));
  const currentUnit = await client.get('/api/author/units/' + unit.id);
  const lesson = await client.action('lesson', '/api/author/lessons', { unitId: unit.id, unitBaseRevision: currentUnit.unit.revision, title: 'A day out — Bảy thử thách', description: '18 câu: làm quen từng dạng, khám phá hai bài đọc và nghe chọn đáp án.', instructions: [{ kind: 'TEXT', text: 'Mỗi phần có một câu hướng dẫn. Hai phần đọc có ba câu mỗi phần, cuối bài có hai câu nghe chọn đáp án. Nhập từ ngay trong đoạn văn; nộp đầy đủ để hoàn thành bài.' }], objectiveIds: [objective.id], questions: slots, activities });
  const published = await client.publish('lessons', lesson.id, 'publish');
  return { unitId: unit.id, lessonId: lesson.id, lessonVersionId: published.publishedVersionId, questionVersions: Object.fromEntries(questions), materialVersions: Object.fromEntries(materials), version: sampleVersion };
}
