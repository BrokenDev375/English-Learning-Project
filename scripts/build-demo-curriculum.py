"""Build the original, offline M02 curriculum and its review manifest. No network calls."""
from pathlib import Path
import csv
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'source/server/src/main/resources/demo'
SOURCE = {'label': 'English Learning Project — câu hỏi tự biên soạn', 'url': None, 'license': 'Dùng cho học tập trong dự án'}
MATERIAL_SOURCE = {'label': 'English Learning Project — nội dung tự biên soạn', 'url': None, 'license': 'Dùng cho học tập trong dự án'}
AUDIO_SOURCE = {'label': 'Nội dung dự án; giọng tổng hợp Microsoft Zira Desktop', 'url': None, 'license': 'Audio demo tạo trên máy phát triển'}
materials, lessons = [], []
OBJECTIVE_NAMES = {'daily-actions':'Mô tả thói quen hằng ngày', 'weekly-activities':'Mô tả hoạt động trong tuần và tần suất', 'order-food':'Gọi món bằng mẫu câu đơn giản', 'buy-groceries':'Hỏi giá và số lượng khi mua thực phẩm', 'ask-directions':'Hỏi và chỉ đường đến một địa điểm', 'plan-journey':'Hỏi giờ khởi hành, vé và thông tin chuyến đi'}

def material(key, kind, title, text='', file=None, legacy=None, alt='', caption=''):
    data = {'kind': kind, 'title': title, 'source': AUDIO_SOURCE if kind == 'AUDIO' else MATERIAL_SOURCE}
    if kind == 'READING': data.update(readingText=text, assetId=None, altText=None)
    elif kind == 'AUDIO': data.update(assetId=None, transcript=text, startSeconds=0, endSeconds=None)
    else: data.update(assetId=None, altText=alt, caption=caption)
    entry = {'seedKey': 'm02-seed-m-' + key, 'legacyAlias': legacy, 'file': file, 'sha256': None, 'data': data}
    if file and (RES / 'materials' / file).exists(): entry['sha256'] = hashlib.sha256((RES / 'materials' / file).read_bytes()).hexdigest()
    materials.append(entry)
    return entry['seedKey']

def sc(prompt, options, correct, explanation, hint, material=None):
    return {'type': 'SINGLE_CHOICE', 'prompt': prompt, 'content': {'options': [{'id': chr(97+i), 'text': text} for i, text in enumerate(options)]}, 'answerKey': {'correctOptionId': chr(97+correct)}, 'explanation': explanation, 'hint': hint, '_material': material}

def fill(prompt, sentence, answers, explanation, hint, material=None):
    parts=[]
    for part in re.split(r'(\{b[1-5]\})', sentence):
        if re.fullmatch(r'\{b[1-5]\}', part): parts.append({'kind': 'BLANK', 'blankId': part[1:-1]})
        elif part: parts.append({'kind': 'TEXT', 'text': part})
    return {'type': 'FILL_BLANK', 'prompt': prompt, 'content': {'parts': parts}, 'answerKey': {'blanks': [{'blankId': f'b{i+1}', 'acceptedAnswers': [a] if isinstance(a,str) else a} for i,a in enumerate(answers)], 'caseSensitive': False, 'normalizeWhitespace': True}, 'explanation': explanation, 'hint': hint, '_material': material}

def match(prompt, pairs, explanation, hint, material=None):
    # IDs represent meaning; a renderer may shuffle either side without changing the key.
    left=[{'id': f'l{i+1}', 'text': a} for i,(a,b) in enumerate(pairs)]
    right=[{'id': f'r{i+1}', 'text': b} for i,(a,b) in enumerate(pairs)]
    return {'type': 'MATCHING', 'prompt': prompt, 'content': {'leftItems': left, 'rightItems': right[::-1]}, 'answerKey': {'pairs': [{'leftId': f'l{i+1}', 'rightId': f'r{i+1}'} for i in range(len(pairs))]}, 'explanation': explanation, 'hint': hint, '_material': material}

def lesson(slug, unit, objective, title, description, example, translation, groups, legacy=None, baseline=None):
    assert [len(g) for g in groups] == [5,5,5]
    questions=[]
    levels=[['EASY','EASY','MEDIUM','EASY','MEDIUM'], ['MEDIUM','EASY','MEDIUM','HARD','MEDIUM'], ['HARD','MEDIUM','HARD','MEDIUM','EASY']]
    for g,(role,rows) in enumerate(zip(['GUIDED','INDEPENDENT','REVIEW_VARIANT'],groups)):
        for i,row in enumerate(rows):
            data={k:v for k,v in row.items() if not k.startswith('_')}
            data.update(topicId=0, objectiveId=0, difficulty=row.get('_difficulty',levels[g][i]), materialVersionId=None, source=SOURCE)
            questions.append({'seedKey': f'm02-seed-q-{slug}-{role.lower()}-{i+1:02}', 'legacyAlias': row.get('_legacy'), 'materialKey': row.get('_material'), 'practiceRole': role, 'position': i, 'data': data})
    lessons.append({'seedKey': 'm02-seed-l-' + slug, 'legacyAlias': legacy, 'legacyBaseline': baseline, 'unitSeedKey': 'm02-catalog-' + unit, 'objectiveCode': objective, 'objectiveName': OBJECTIVE_NAMES[objective],
                    'data': {'title': title, 'description': description, 'instructions': [{'kind':'TEXT','text':description}, {'kind':'EXAMPLE','text':example,'translation':translation}], 'objectiveIds':[], 'questions':[]}, 'questions':questions})

daily_text='Every morning, Linh gets up at seven. She eats breakfast at home and takes the bus to school. In the evening, she reads a book and goes to bed at ten.'
dr=material('daily-morning-reading','READING','A day with Linh',daily_text,legacy='m02-material-reading')
da=material('daily-morning-audio','AUDIO','Listen: Linh’s daily routine',daily_text,'daily-routine.wav',legacy='m02-material-audio')
di=material('daily-morning-image','IMAGE','Linh’s routine in pictures',file='daily-routine.png',legacy='m02-material-image',alt='Lịch của Linh: dậy lúc 7 giờ, ăn sáng 7 giờ 30, đi học 8 giờ, đi ngủ 22 giờ.',caption='Look at the times. Talk about her routine.')
legacy_read=sc('What time does Linh get up?',['At seven.','At eight.','At ten.'],0,'The passage says: Linh gets up at seven.','Find the time after ‘gets up’ in the passage.',dr)
legacy_read['content']={'options':[{'id':'opt-seven','text':'At seven.'},{'id':'opt-eight','text':'At eight.'},{'id':'opt-ten','text':'At ten.'}]};legacy_read['answerKey']={'correctOptionId':'opt-seven'};legacy_read.update(_legacy='m02-authoring-question-reading',_difficulty='EASY')
legacy_audio=fill('Listen and complete Linh’s evening activity.','In the evening, she {b1} a book.',['reads'],'The audio says: In the evening, she reads a book.','Listen for the verb before ‘a book’.',da)
legacy_audio['content']['parts'][1]['blankId']='activity';legacy_audio['answerKey']['blanks'][0]['blankId']='activity';legacy_audio.update(_legacy='m02-authoring-question-audio',_difficulty='EASY')
legacy_image=match('Match the activities with the times in Linh’s schedule.', [('Get up','07:00'),('Go to school','08:00'),('Go to bed','22:00')], 'Her schedule shows getting up at 07:00, school at 08:00 and bedtime at 22:00.','Read each time next to the activity in the picture.',di)
legacy_image['content']={'leftItems':[{'id':'wake','text':'Get up'},{'id':'school','text':'Go to school'},{'id':'sleep','text':'Go to bed'}],'rightItems':[{'id':'seven','text':'07:00'},{'id':'eight','text':'08:00'},{'id':'ten','text':'22:00'}]};legacy_image['answerKey']={'pairs':[{'leftId':'wake','rightId':'seven'},{'leftId':'school','rightId':'eight'},{'leftId':'sleep','rightId':'ten'}]};legacy_image.update(_legacy='m02-authoring-question-image',_difficulty='EASY')
lesson('daily-morning','daily-routines','daily-actions','A day with Linh — Morning routine','Mô tả thói quen bằng hiện tại đơn; đọc lịch sinh hoạt và nghe các mốc thời gian.','She gets up at seven.','Cô ấy thức dậy lúc bảy giờ.',[
 [legacy_read,
  fill('Use the present-simple form of “eat” to describe Mai’s usual breakfast.','Mai {b1} breakfast at home.',['eats'],'With Mai (she), the present simple verb eat takes -s.','Mai is one person: use the she form.'),
  legacy_image,
  sc('Listen. How does Linh travel to school?',['By bike.','By bus.','On foot.'],1,'The audio says that she takes the bus to school.','Listen for the transport word.',da),
  fill('Listen and complete the place.','She eats breakfast at {b1}.',['home'],'The speaker says: She eats breakfast at home.','Listen after ‘at’.',da)],
 [legacy_audio,
  sc('According to the reading, when does Linh go to bed?',['At eight.','At nine.','At ten.'],2,'The final clause says that she goes to bed at ten.','Find the last time in the passage.',dr),
  match('Match each part of Linh’s day with its activity in the reading.',[('Morning','Get up at seven'),('Journey to school','Take the bus'),('Evening','Read a book')],'The passage gives these three activities in that order.','Read the morning, travel and evening sentences.',dr),
  sc('Choose the natural question for the answer: “I get up at six.”',['What time do you get up?','Where do you get up?','Who do you get up?'],0,'What time asks for a time; six is a time.','Choose the question word for a clock time.'),
  match('Match the routine verbs with the words that complete them.',[('brush','my teeth'),('take','a shower'),('do','my homework')],'The common combinations are brush my teeth, take a shower and do my homework.','Think of everyday verb phrases.')],
 [match('Nora starts work at 09:00, has lunch at 12:30 and finishes work at 17:00. Match her activities with their times.',[('Start work','09:00'),('Have lunch','12:30'),('Finish work','17:00')],'All three times are stated in Nora’s workday, a different routine from Linh’s.','Use Nora’s schedule in the question.'),
  fill('Use the correct form of “go” in this negative sentence about Ben.','Ben does not {b1} to school on Sundays.',['go'],'After does not, use the base form go, not goes.','The auxiliary does already carries the third-person ending.'),
  sc('Eva usually leaves home at 8:10. Today she leaves at 8:25. How much later does she leave?',['Five minutes.','Fifteen minutes.','Twenty-five minutes.'],1,'8:25 is fifteen minutes after 8:10.','Compare the minutes after eight.'),
  fill('Omar and I cook together every Friday. Complete the sentence.','We {b1} dinner every Friday.',['cook'],'The subject we takes the base present-simple verb cook.','Use the verb from the question with we.'),
  match('Match the new speakers with the frequency in what they say.',[('I swim every day.','Daily'),('I visit on Saturday each week.','Weekly'),('I call on the first day of each month.','Monthly')],'Every day is daily, each week is weekly, and each month is monthly.','Look for day, week and month.')]
],legacy='m02-authoring-walkthrough',baseline={'title':'A day with Linh — Bài mẫu','description':'Bài mẫu ngắn với đoạn đọc, audio, ảnh và ba dạng câu hỏi.','instructions':[{'kind':'TEXT','text':'Read about Linh, listen to her routine and use the picture to practise telling the time.'},{'kind':'EXAMPLE','text':'She gets up at seven.','translation':'Cô ấy thức dậy lúc bảy giờ.'}]})

text='On Saturday, Nam visits the community library with his sister. The library opens at nine. They arrive at nine thirty. Nam borrows two science books, and his sister chooses a comic. They leave before lunch and walk home together.'
audio='Hello, I am Nam. On weekdays I cycle to school at seven fifteen. After school, I play badminton with An. On Saturdays, I go to the library with my sister. We walk there because it is near our home.'
r=material('daily-library-reading','READING','Saturday at the library',text)
a=material('daily-library-audio','AUDIO','Listen: Nam’s week',audio,'daily-library.wav')
lesson('daily-library','daily-routines','weekly-activities','Nam’s week and weekend','Phân biệt hoạt động thường ngày và cuối tuần; hỏi về thời gian, phương tiện và tần suất.','He visits the library on Saturdays.','Bạn ấy đến thư viện vào các ngày thứ Bảy.',[
 [sc('Read. Who goes to the library with Nam?',['His brother.','His sister.','His teacher.'],1,'The first sentence says that Nam visits with his sister.','Look after the word with.',r),
  fill('Use the present-simple form of “read” for this regular activity.','My sister {b1} comics at the library.',['reads'],'My sister is singular, so read becomes reads.','Use the present-simple she form.'),
  match('Match the library words with their meanings.',[('borrow','Take something and return it later'),('return','Give something back'),('choose','Decide which one you want')],'Borrow, return and choose describe different actions when using a library.','Think about what happens before and after using a book.'),
  sc('Listen. How does Nam go to school on weekdays?',['He cycles.','He walks.','He takes a train.'],0,'Nam says: On weekdays I cycle to school.','Listen to the sentence about weekdays.',a),
  fill('Listen and complete the time.','Nam cycles to school at {b1}.',[['seven fifteen','7:15','07:15']],'The recording gives the departure time as seven fifteen.','Listen for the two numbers after at.',a)],
 [fill('Listen and complete Nam’s after-school sport.','He plays {b1} with An.',['badminton'],'Nam says that he plays badminton with An after school.','The sport comes after play.',a),
  sc('Read. How many science books does Nam borrow?',['One.','Two.','Three.'],1,'The passage says he borrows two science books.','Find science books in the passage.',r),
  match('Match each library event with the detail in the reading.',[('Opening time','09:00'),('Arrival time','09:30'),('Time to leave','Before lunch')],'The library opens at nine, they arrive at nine thirty and leave before lunch.','Read the times carefully; opening is different from arrival.',r),
  sc('Which question asks about frequency?',['How often do you visit the library?','Where is your library card?','What colour is the book?'],0,'How often asks how frequently an activity happens.','Look for the phrase meaning frequency.'),
  match('Match each habit with its natural time phrase.',[('I sleep','at night'),('I eat my morning meal','in the morning'),('I usually relax after work','in the evening')],'At night, in the morning and in the evening are common time expressions.','Notice the different preposition used with night.')],
 [match('Lena feeds her cat at 06:45, catches a bus at 07:20 and starts class at 08:00. Match the events with their times.',[('Feed the cat','06:45'),('Catch the bus','07:20'),('Start class','08:00')],'These are Lena’s own three times, not Nam’s library schedule.','Use the new schedule in the prompt.'),
  fill('Complete the question to ask about Rui’s usual Saturday activity.','What {b1} Rui do on Saturdays?',['does'],'Rui is one person; present-simple questions use does plus the base verb.','Choose do or does for he.'),
  sc('Ida practises piano on Monday and Thursday every week. How often does she practise?',['Once a week.','Twice a week.','Every day.'],1,'Monday and Thursday give two practice days in one week.','Count the two days, not the hours.'),
  fill('Complete the sentence about two friends.','Leo and Kim {b1} board games after dinner.',['play'],'A plural subject takes the base form play.','Use play in the present simple with they.'),
  match('Match the new diary notes with their categories.',[('07:10 — water the plants','Morning routine'),('16:30 — finish school','Afternoon routine'),('20:00 — write in a diary','Evening routine')],'07:10 is morning, 16:30 is afternoon and 20:00 is evening.','Use the 24-hour clock to choose each part of the day.')]
])

text='At Green Cup Café, a bowl of soup costs four dollars and a cheese sandwich costs five dollars. Apple juice costs two dollars. Tea costs one dollar. The café serves lunch from eleven to two. Mina orders soup and apple juice. She sits by the window.'
audio='Good afternoon. I would like a cheese sandwich and a cup of tea, please. I do not want sugar in my tea. Can I pay by card? I will eat here, at the small table beside the door. Thank you.'
r=material('food-cafe-reading','READING','Lunch at Green Cup Café',text)
a=material('food-cafe-audio','AUDIO','Listen: A café order',audio,'food-cafe.wav')
lesson('food-cafe','food-shopping','order-food','Ordering lunch at a café','Đọc thực đơn, gọi món lịch sự và hỏi cách thanh toán trong một quán cà phê giả định.','I would like a sandwich, please.','Cho tôi một chiếc bánh mì kẹp. ',[
 [sc('Read the menu. How much is a cheese sandwich?',['Two dollars.','Four dollars.','Five dollars.'],2,'The menu lists a cheese sandwich at five dollars.','Find cheese sandwich, not soup.',r),
  fill('Complete this polite order using the verb “like”.','I would {b1} a bowl of soup, please.',['like'],'I would like is a polite phrase for ordering food.','Use the verb in the expression would like.'),
  match('Match the menu words with the usual serving words.',[('a bowl of','soup'),('a cup of','tea'),('a glass of','juice')],'Soup is served in a bowl, tea in a cup, and juice in a glass.','Think about the container for each food or drink.'),
  sc('Listen. Which drink does the customer order?',['Tea.','Apple juice.','Coffee.'],0,'The customer asks for a cup of tea.','Listen after and a cup of.',a),
  fill('Listen and complete the request about tea.','The customer does not want {b1}.',['sugar'],'The recording says: I do not want sugar in my tea.','Listen for what the customer wants to leave out.',a)],
 [fill('Listen and complete the payment question.','Can I pay by {b1}?',['card'],'The customer asks whether payment by card is possible.','Listen after pay by.',a),
  sc('Read. What does Mina order?',['Soup and apple juice.','A sandwich and tea.','Soup and tea.'],0,'The reading states that Mina orders soup and apple juice.','Mina’s order is in the last two sentences.',r),
  match('Match each item with its menu price.',[('Soup','$4'),('Apple juice','$2'),('Tea','$1')],'The listed prices are soup four, juice two and tea one dollar.','Read each item’s own price.',r),
  sc('Which reply naturally follows “For here or to go?”',['To go, please.','At six o’clock.','It is blue.'],0,'To go means the customer will take the order away.','The question asks where the food will be eaten.'),
  match('Match the café requests with what the customer needs.',[('Could I see the menu?','A list of food and prices'),('Could I have the bill?','The amount to pay'),('Could I have some water?','A drink')],'Menu, bill and water each answer a different customer need.','Think about what the named object is used for.')],
 [match('At the fictional Blue Bowl shop, noodles cost $6, rice costs $3 and lemonade costs $2. Match each item with its price.',[('Noodles','$6'),('Rice','$3'),('Lemonade','$2')],'These are the three prices in the new Blue Bowl menu.','Use this shop’s menu, not Green Cup Café’s.'),
  fill('Complete the new customer’s request with “have” or “get”.','Could I {b1} a glass of water, please?',[['have','get']],'Could I have and Could I get are natural requests for a drink.','Choose a base verb used to request a drink.'),
  sc('A snack costs $3 and a drink costs $2. Sam pays with $10. How much change should he get?',['Three dollars.','Five dollars.','Seven dollars.'],1,'The total is five dollars; ten minus five leaves five dollars change.','Add the two prices, then subtract from ten.'),
  fill('Use the plural of “cup” in this order for two people.','We would like two {b1} of tea.',['cups'],'Two requires the plural cups in two cups of tea.','Use the plural of cup.'),
  match('Match the new requests with the relevant dietary detail.',[('No milk, please.','Leave out milk'),('No ice, please.','Serve without ice'),('Less salt, please.','Reduce the amount of salt')],'No asks to omit an ingredient; less asks for a smaller amount.','Distinguish leaving something out from reducing it.')]
])

text='On Sunday, Hana buys fruit at River Market. Apples cost three dollars a kilo, bananas cost two dollars a kilo, and oranges cost four dollars a kilo. She buys one kilo of apples and two kilos of bananas. She brings her own bag. The stall closes at noon.'
audio='Hello. Could I have half a kilo of tomatoes and one kilo of potatoes, please? Are these tomatoes fresh today? I have my own bag. Here is ten dollars. Thank you. Please put the receipt inside the bag.'
r=material('food-market-reading','READING','Shopping at River Market',text)
a=material('food-market-audio','AUDIO','Listen: At the vegetable stall',audio,'food-market.wav')
lesson('food-market','food-shopping','buy-groceries','Buying fruit and vegetables','Hỏi giá, số lượng và dùng các cụm đo lường khi mua hàng tại một khu chợ giả định.','How much are the apples?','Táo giá bao nhiêu?',[
 [sc('Read. Which fruit costs two dollars a kilo?',['Apples.','Bananas.','Oranges.'],1,'Bananas cost two dollars per kilo in the reading.','Find the item beside the price two dollars.',r),
  fill('Complete the question about the price of these oranges.','How {b1} are these oranges?',['much'],'How much asks for a price.','Use the question phrase for price, not number.'),
  match('Match the quantities with their equivalent amounts.',[('half a kilo','500 grams'),('one kilo','1,000 grams'),('two kilos','2,000 grams')],'One kilo is one thousand grams, so half is five hundred and two kilos is two thousand.','Use 1 kilo = 1,000 grams.'),
  sc('Listen. What does the customer buy half a kilo of?',['Potatoes.','Tomatoes.','Carrots.'],1,'The customer asks for half a kilo of tomatoes.','Listen to the first quantity in the order.',a),
  fill('Listen and complete the second vegetable.','The customer buys one kilo of {b1}.',['potatoes'],'The recording says one kilo of potatoes.','The second item follows and one kilo of.',a)],
 [fill('Listen. Where should the seller put the receipt?','Put the receipt inside the {b1}.',['bag'],'The last request is to put the receipt inside the bag.','Listen to the final sentence.',a),
  sc('Read. How much does Hana’s fruit cost in total?',['Five dollars.','Seven dollars.','Nine dollars.'],1,'One kilo of apples is $3, and two kilos of bananas cost $4; the total is $7.','Multiply the banana price by two before adding.',r),
  match('Match each detail with Hana’s shopping trip.',[('Shopping day','Sunday'),('Bag','Her own bag'),('Stall closing time','Noon')],'The passage says Sunday, her own bag, and closing at noon.','Look for the day, the bag and the last sentence.',r),
  sc('Which question asks about the number of apples in a bag?',['How many apples are there?','How much is the bag?','Where is the market?'],0,'Apples are countable, so how many asks for their number.','Choose the phrase used with countable plural nouns.'),
  match('Match the shopping phrases with their purposes.',[('How much is it?','Ask the price'),('That is all, thank you.','Finish the order'),('Do you have a smaller size?','Ask for a different size')],'The three phrases ask a price, end an order and request another size.','Identify what the shopper wants to do.')],
 [match('At Oak Farm’s fictional stall, pears are $5/kg, carrots are $2/kg and beans are $4/kg. Match the items with their prices.',[('Pears','$5/kg'),('Carrots','$2/kg'),('Beans','$4/kg')],'All prices come from the new Oak Farm stall in the prompt.','Do not reuse River Market prices.'),
  fill('Complete the request with a weight unit meaning 1,000 grams.','I would like half a {b1} of grapes.',[['kilo','kilogram']],'Kilo and kilogram both name the metric unit of 1,000 grams.','Use the metric weight unit from the lesson.'),
  sc('A bag contains 6 peaches. Amir gives 2 to a friend and buys 3 more. How many peaches does he have now?',['Five.','Seven.','Nine.'],1,'Six minus two plus three equals seven peaches.','Follow the giving and buying actions in order.'),
  fill('Use the possessive form of “she” for this shopper’s own bag.','She brings {b1} own bag.',['her'],'She uses the possessive adjective her before own bag.','Choose the possessive form that matches she.'),
  match('Match the new stall labels with what they mean.',[('Sold out','None left to buy'),('Special offer','A promotion on an item'),('Exact change, please','Pay the precise amount')],'Sold out means unavailable; a special offer is a promotion; exact change is the precise price.','Read each label as a practical message to a shopper.')]
])

text='The town museum is on King Street, opposite the post office. From the bus stop, walk straight to the traffic lights. Turn left into King Street. The museum is the second building on the right. It opens at ten and closes at five. Tickets cost three dollars.'
audio='Excuse me, how can I get to the park? Walk along Pine Road and cross at the zebra crossing. Turn right after the bakery. The park entrance is beside the swimming pool. It is about a ten-minute walk from here.'
r=material('travel-museum-reading','READING','Finding the town museum',text)
a=material('travel-museum-audio','AUDIO','Listen: Directions to the park',audio,'travel-museum.wav')
lesson('travel-museum','travel-directions','ask-directions','Finding places in town','Hỏi đường, theo dõi chỉ dẫn rẽ trái/phải và xác định vị trí các địa điểm trong một thị trấn giả định.','Turn left at the traffic lights.','Rẽ trái tại đèn giao thông.',[
 [sc('Read. Where is the museum?',['On King Street.','On Pine Road.','Inside the post office.'],0,'The first sentence places the museum on King Street.','Look for the street name in the first sentence.',r),
  fill('Complete the instruction to continue without turning.','Go {b1} on.',['straight'],'Go straight on means continue in the same direction.','Use the direction word meaning not left or right.'),
  match('Match the direction phrases with their meanings.',[('turn left','Change direction to the left'),('turn right','Change direction to the right'),('cross the road','Go from one side of the road to the other')],'Each phrase describes a different movement through town.','Picture what a walker does for each instruction.'),
  sc('Listen. After which place should the walker turn right?',['The museum.','The bakery.','The post office.'],1,'The speaker says: Turn right after the bakery.','Listen for the place after after.',a),
  fill('Listen and complete the landmark.','The park entrance is beside the swimming {b1}.',['pool'],'The entrance is beside the swimming pool.','Complete the two-word place swimming pool.',a)],
 [fill('Listen. About how long does the walk take?','It is about a {b1}-minute walk.',[['ten','10']],'The speaker says it is about a ten-minute walk.','Listen for the length of the walk.',a),
  sc('Read. Which building on the right is the museum?',['The first.','The second.','The third.'],1,'The passage identifies the museum as the second building on the right.','Find the ordinal number before building.',r),
  match('Match the museum details with the reading.',[('Opposite landmark','Post office'),('Opening time','10:00'),('Ticket price','$3')],'The museum is opposite the post office, opens at ten and charges three dollars.','Use three different details from the passage.',r),
  sc('Which is the most suitable way to begin asking a stranger for directions?',['Excuse me, where is the station?','Give me your station.','I am a station.'],0,'Excuse me politely gets attention before a directions question.','Look for the polite opening and a clear place question.'),
  match('Match the location phrases with their meanings.',[('next to','Directly beside'),('opposite','Across from'),('between','In the middle of two things')],'Next to, opposite and between describe distinct relative positions.','Imagine three buildings on a street.')],
 [match('A map shows the clinic north of the square, the school east of it and the café west of it. Match each place with its direction from the square.',[('Clinic','North'),('School','East'),('Café','West')],'The new map gives the clinic north, school east and café west.','Use the square as your starting reference point.'),
  fill('Complete this new route instruction.','Turn {b1} after the bank to follow the arrow →.',['right'],'The arrow points to the right, so the instruction is turn right.','Follow the direction of the arrow.'),
  sc('Nia walks past a bank, then a cinema, then a hotel. The library is between the cinema and the hotel. Which place does she pass immediately before the library?',['The bank.','The cinema.','The hotel.'],1,'The library comes after the cinema and before the hotel.','Place the library into the order given.'),
  fill('The shop is in the middle of two landmarks. Complete its location.','The shop is {b1} the bank and the hotel.',['between'],'Between links a place to the two landmarks on either side.','The word must work with and two landmarks.'),
  match('Match the new pedestrian signs with the action to take.',[('STOP','Do not continue past this point'),('Pedestrian crossing','Use this place to cross the road'),('No entry','Do not enter this way')],'Each sign gives a specific instruction to a pedestrian.','Think about safe, permitted movement rather than a destination.')]
])

text='On Saturday, Tuan and his cousin take a train to Lake Town. Their train leaves at eight twenty and arrives at nine ten. A return ticket costs twelve dollars. They plan to visit the lake in the morning and the old market after lunch. Their return train leaves at four thirty.'
audio='This is an announcement for passengers travelling to Hill City. The nine forty bus will leave from platform three. Please arrive ten minutes before departure. Keep your ticket with you. The journey takes fifty minutes. Thank you for travelling with us.'
r=material('travel-trip-reading','READING','A train trip to Lake Town',text)
a=material('travel-trip-audio','AUDIO','Listen: The bus station announcement',audio,'travel-trip.wav')
lesson('travel-trip','travel-directions','plan-journey','Planning a weekend trip','Đọc giờ đi/đến, phân biệt vé một chiều/khứ hồi và nghe thông báo tại bến xe giả định.','What time does the train leave?','Tàu khởi hành lúc mấy giờ?',[
 [sc('Read. How do Tuan and his cousin travel to Lake Town?',['By train.','By bus.','By plane.'],0,'The first sentence says that they take a train.','Find the transport in the first sentence.',r),
  fill('Complete the departure-time question with “leave” or “depart”.','What time does the train {b1}?',[['leave','depart']],'Both leave and depart are base verbs used after does in a departure-time question.','Use a verb meaning depart.'),
  match('Match the travel words with their meanings.',[('departure','The time a service leaves'),('arrival','The time a service reaches its destination'),('return ticket','A ticket for going and coming back')],'Departure is leaving, arrival is reaching the destination, and return covers both directions.','Distinguish leaving, reaching and ticket type.'),
  sc('Listen. Which platform does the Hill City bus use?',['Platform one.','Platform two.','Platform three.'],2,'The announcement states platform three.','Listen for the number after platform.',a),
  fill('Listen and complete the departure time.','The bus leaves at {b1}.',[['nine forty','9:40','09:40']],'The announcement is for the nine forty bus.','Listen to the time before bus.',a)],
 [fill('Listen. How long does the bus journey take?','The journey takes {b1} minutes.',[['fifty','50']],'The final travel detail is a journey time of fifty minutes.','Listen after The journey takes.',a),
  sc('Read. What do Tuan and his cousin plan to do after lunch?',['Visit the old market.','Return home immediately.','Visit the lake for the first time.'],0,'The reading places the old market after lunch and the lake in the morning.','Distinguish the morning activity from the afternoon plan.',r),
  match('Match the train-trip details with the reading.',[('Outbound departure','08:20'),('Outbound arrival','09:10'),('Return departure','16:30')],'The text gives eight twenty, nine ten and four thirty in the afternoon.','Match each time with the correct leg of the trip.',r),
  sc('Which request asks for a ticket for a journey in only one direction?',['A single ticket, please.','A return ticket, please.','A table for two, please.'],0,'A single ticket covers one direction; a return ticket includes coming back.','Choose the ticket type that does not include a return.'),
  match('Match the station questions with the information requested.',[('Which platform?','Where to board'),('How much is a ticket?','The fare'),('How long is the journey?','The travel duration')],'These questions ask for boarding location, price and duration respectively.','Focus on platform, how much and how long.')],
 [match('A new timetable lists the coast bus at 07:15, the airport bus at 08:05 and the village bus at 09:50. Match each route with its departure.',[('Coast','07:15'),('Airport','08:05'),('Village','09:50')],'The departures belong to a new timetable and different destinations.','Read the route before selecting its time.'),
  fill('Complete the advice using the travel document named in the bus announcement.','Please keep your {b1} ready for inspection.',['ticket'],'The announcement asks passengers to keep their ticket with them.','Use the travel document named in the announcement lesson.'),
  sc('A ferry leaves at 14:10. Boarding closes 15 minutes earlier. What is the latest boarding time?',['13:45.','13:55.','14:25.'],1,'Fifteen minutes before 14:10 is 13:55.','Subtract ten minutes, then five more.'),
  fill('Complete the question about the destination of a coach.','{b1} does this coach go?',['where'],'Where asks about a destination or place.','Choose the question word for a place.'),
  match('Match the new journey notes with the transport used.',[('We sail across the bay.','Ferry'),('We fly above the clouds.','Plane'),('We follow the railway tracks.','Train')],'Sail suggests a ferry, fly a plane, and railway tracks a train.','Identify the transport from the movement or route.')]
])

def main():
    assert len(lessons)==6 and sum(len(l['questions']) for l in lessons)==90
    keys=[q['seedKey'] for l in lessons for q in l['questions']]
    assert len(set(keys))==90
    from collections import Counter
    assert Counter(q['data']['type'] for l in lessons for q in l['questions'])=={'SINGLE_CHOICE':30,'FILL_BLANK':30,'MATCHING':30}
    prompts=[q['data']['prompt'] for l in lessons for q in l['questions']]
    assert len(set(prompts))==90
    curriculum={'schemaVersion':1,'datasetVersion':'m02-demo-1.0.0','materials':materials,'lessons':lessons}
    RES.mkdir(parents=True,exist_ok=True)
    (RES/'curriculum-v1.json').write_text(json.dumps(curriculum,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    out=ROOT/'statics/dataset';out.mkdir(parents=True,exist_ok=True)
    with (out/'m02-question-manifest.csv').open('w',encoding='utf-8-sig',newline='') as f:
        writer=csv.writer(f);writer.writerow(['seedKey','lessonSeedKey','unitSeedKey','objectiveCode','type','difficulty','practiceRole','position','materialSeedKey','prompt','source'])
        for l in lessons:
            for q in l['questions']: writer.writerow([q['seedKey'],l['seedKey'],l['unitSeedKey'],l['objectiveCode'],q['data']['type'],q['data']['difficulty'],q['practiceRole'],q['position'],q['materialKey'] or '',q['data']['prompt'],SOURCE['label']])
    with (out/'m02-material-manifest.csv').open('w',encoding='utf-8-sig',newline='') as f:
        writer=csv.writer(f);writer.writerow(['seedKey','kind','title','file','sha256','source','license'])
        for m in materials: writer.writerow([m['seedKey'],m['data']['kind'],m['data']['title'],m['file'] or '',m['sha256'] or '',m['data']['source']['label'],m['data']['source']['license']])
    review=['# M02 — bảng đối chiếu nội dung và đáp án', '', 'Nguồn chuẩn: `source/server/src/main/resources/demo/curriculum-v1.json`. Tất cả tình huống, giá và lịch trình trong bộ seed là giả định để học tiếng Anh; không phải thông tin đi lại/mua sắm thực tế.', '', 'Đã rà soát câu hỏi/đáp án với đoạn đọc, transcript và ngữ cảnh; câu điền từ giới hạn rõ động từ/ngữ cảnh hoặc nhận nhiều đáp án hợp lệ. Nội dung không tự đặt lại khi seed chạy lại.', '']
    def cell(value): return str(value).replace('|','\\|').replace('\n',' ')
    for l in lessons:
        review.extend(['## '+l['data']['title'], '', 'Mục tiêu: '+l['objectiveName']+'.', '', '| Nhóm / vị trí | Dạng | Câu hỏi | Đáp án chấp nhận | Giải thích |', '|---|---|---|---|---|'])
        for q in l['questions']:
            d=q['data'];c=d['content'];a=d['answerKey']
            if d['type']=='SINGLE_CHOICE': answer=next(o['text'] for o in c['options'] if o['id']==a['correctOptionId'])
            elif d['type']=='FILL_BLANK': answer='; '.join(b['blankId']+': '+' / '.join(b['acceptedAnswers']) for b in a['blanks'])
            else:
                left={x['id']:x['text'] for x in c['leftItems']};right={x['id']:x['text'] for x in c['rightItems']}
                answer='; '.join(left[p['leftId']]+' → '+right[p['rightId']] for p in a['pairs'])
            review.append('| '+' | '.join(map(cell,[q['practiceRole']+' / '+str(q['position']+1),d['type'],d['prompt'],answer,d['explanation']]))+' |')
        review.append('')
    (out/'m02-editorial-review.md').write_text('\n'.join(review)+'\n',encoding='utf-8')
    print('Built 6 lessons / 90 unique question roots / 13 materials; 30 questions per type and role.')

if __name__=='__main__': main()
