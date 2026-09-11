# -*- coding: utf-8 -*-
r"""QA용 시드 데이터.

실행 중인 앱의 **공개 API만** 써서 넣는다 — DB에 직접 쓰지 않는다. 그래서 들어가는 경로가
곧 프런트가 부를 경로이고, 시드가 성공한다는 것 자체가 그 경로들이 살아 있다는 뜻이다.

    docker compose up -d
    .\gradlew.bat bootRun --args='--spring.profiles.active=local'
    python tools/seed-qa.py

주소를 바꾸려면 TWELVEBOOKS_URL 환경변수. 기본은 http://localhost:8080.

**여러 번 돌려도 같은 상태가 된다.** 쌓기만 하는 것이 아니라 목표 상태로 수렴한다 —
없는 것은 채우고, 시드 계정의 감상평·팔로우 중 이 파일의 목록에 없는 것은 지운다.
그래야 예전 시드와 손으로 넣어 본 데이터가 겹겹이 남지 않는다.

**시드 계정(test1~5)이 손으로 쓴 글은 지워진다.** 남겨야 할 글이 있으면 아래 POSTS에 적는다.

계정이 없으면 만든다. 비밀번호는 전부 123456789 — 로컬 QA 전용이고 운영에 쓰지 않는다.
"""
import json
import os
import urllib.parse
import urllib.request
import urllib.error

BASE = os.environ.get("TWELVEBOOKS_URL", "http://localhost:8080").rstrip("/") + "/api/v1"
PASSWORD = "123456789"

# 사람마다 읽는 방식이 다르게 잡았다. 아래 서재·감상평이 그 성격을 따라간다 —
# 이름만 사람 같고 데이터는 무작위면 화면에서 금방 티가 난다.
ACCOUNTS = [
    # QA의 주인공 다섯. 팔로우 관계와 서재가 이 사람들 중심으로 짜여 있다.
    ("test1@test.com", "test1", "김서연",
     "지하철에서 20분씩. 기술서랑 소설을 번갈아 읽습니다."),
    ("test2@test.com", "test2", "박도윤",
     "완독보다 완주. 읽다 만 책도 그대로 남겨 둡니다."),
    ("test3@test.com", "test3", "이하은",
     "같은 책을 다시 읽는 걸 좋아해요. 인생책은 데미안."),
    ("test4@test.com", "test4", "최준우",
     "두꺼운 책만 골라 읽는 편입니다. 요즘은 총 균 쇠."),
    ("test5@test.com", "test5", "정민서",
     "독서 기록 이제 막 시작했습니다."),

    # 아무도 팔로우하지 않는 독자들. 홈(= 팔로우하지 않은 사람들의 글)을 채우는 것이
    # 이 사람들이다 — 이들이 없으면 서로 다 팔로우한 다섯 명의 홈은 거의 빈다.
    ("reader1@test.com", "yungaram", "윤가람", "시집을 주로 읽습니다. 한 편씩 천천히."),
    ("reader2@test.com", "hanjiwoo", "한지우", "추리소설 아니면 잘 안 읽혀요."),
    ("reader3@test.com", "seominjae", "서민재", "출근 전 30분 독서 3년째."),
    ("reader4@test.com", "ohsehun", "오세훈", "과학책 읽고 아이한테 설명해 주는 게 취미."),
    ("reader5@test.com", "baesua", "배수아", "고전만 읽습니다. 느리게 읽는 편."),
    ("reader6@test.com", "imhaneul", "임하늘", "에세이랑 산문. 밑줄 긋는 맛으로 읽어요."),
    ("reader7@test.com", "kangtaeo", "강태오", "경제경영서 위주. 요약해서 남깁니다."),
    ("reader8@test.com", "moonsori", "문소리", "아이랑 같이 읽은 책을 기록합니다."),
    ("reader9@test.com", "shinyujin", "신유진", "판타지 정주행 중. 밤새우기 일쑤."),
    ("reader10@test.com", "joeunbyul", "조은별", "역사책 읽고 연표 그리는 게 습관."),
]


def call(method, path, body=None, token=None):
    """(상태코드, 파싱된 본문) 을 돌려준다. 오류 응답도 예외로 만들지 않는다."""
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json; charset=UTF-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read().decode("utf-8")
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return e.code, json.loads(raw) if raw else None
        except json.JSONDecodeError:
            return e.code, raw


def login_or_signup(email, handle, display_name, bio):
    status, body = call("POST", "/auth/login", {"email": email, "password": PASSWORD})
    if status != 200:
        status, body = call("POST", "/auth/signup", {
            "email": email, "password": PASSWORD,
            "handle": handle, "displayName": display_name,
        })
        if status != 201:
            raise SystemExit("가입 실패 %s: %s %s" % (email, status, body))
        status, body = call("POST", "/auth/login", {"email": email, "password": PASSWORD})
    token = body["accessToken"]
    # 이름과 소개를 매번 덮어쓴다. 이미 있던 계정은 handle이 그대로 이름에 들어가 있곤 한데
    # (test2, test3 …) 그 상태로는 화면이 테스트 화면처럼 보인다.
    # PATCH는 보내지 않은 필드를 건드리지 않으므로 현재 프로필도 함께 돌려받는다.
    _, profile = call("PATCH", "/me", {"displayName": display_name, "bio": bio}, token)
    return token, profile["handle"]


def register_book(token, query):
    status, page = call("GET", "/books/search?q=" + urllib.parse.quote(query) + "&page=1", token=token)
    if status != 200 or not page["items"]:
        print("  검색 실패/결과 없음: %s (%s)" % (query, status))
        return None
    item = page["items"][0]
    status, book = call("POST", "/books", {
        "isbn13": item["isbn13"] or "",
        "title": item["title"],
        "authors": item["authors"],
        "publisher": item.get("publisher"),
        "thumbnailUrl": item.get("thumbnailUrl"),
        "publishedAt": item.get("publishedAt"),
        "signature": item["signature"],
    }, token)
    if status != 201:
        print("  등록 실패: %s → %s %s" % (query, status, book))
        return None
    return book


def shelve(token, book_id, status_name, page_count=None, current_page=None, rating=None):
    code, reading = call("POST", "/readings", {"bookId": book_id, "status": status_name}, token)
    if code == 409:  # 이미 담긴 책 — 기존 기록을 그대로 쓴다
        return None
    if code != 201:
        print("  담기 실패 book=%s: %s %s" % (book_id, code, reading))
        return None
    patch = {}
    if page_count is not None:
        patch["pageCount"] = page_count
    if current_page is not None:
        patch["currentPage"] = current_page
    if rating is not None:
        patch["rating"] = rating
    if patch:
        call("PATCH", "/readings/" + str(reading["id"]), patch, token)
    return reading


def existing_posts(token, handle):
    """이미 쓴 감상평을 {본문: id}로. 있는 것은 건너뛰고 없는 것은 지우는 데 쓴다."""
    found, cursor = {}, None
    while True:
        path = "/users/%s/posts?size=50" % handle + ("&cursor=%d" % cursor if cursor else "")
        code, page = call("GET", path, token=token)
        if code != 200:
            return found
        for item in page["items"]:
            found[item["content"]] = item["id"]
        if not page["hasNext"]:
            return found
        cursor = page["nextCursor"]


def write_post(token, book_id, content, from_page=None, to_page=None, spoiler=False):
    body = {"bookId": book_id, "content": content, "spoiler": spoiler}
    if from_page is not None:
        body["fromPage"] = from_page
    if to_page is not None:
        body["toPage"] = to_page
    code, post = call("POST", "/posts", body, token)
    if code != 201:
        print("  감상평 실패: %s %s" % (code, post))
    return post


# ── 1. 계정 ────────────────────────────────────────────────
print("계정")
users = {}
for email, handle, name, bio in ACCOUNTS:
    token, real_handle = login_or_signup(email, handle, name, bio)
    users[handle] = {"token": token, "handle": real_handle, "name": name}
    print("  %-6s %-8s %-6s %s" % (handle, real_handle, name, bio))

# ── 2. 책 ─────────────────────────────────────────────────
print("\n책")
QUERIES = [
    "클린 코드", "토지 박경리", "데미안", "미움받을 용기", "사피엔스",
    "1984 조지 오웰", "코스모스 칼 세이건", "어린 왕자", "노르웨이의 숲", "총 균 쇠",
    "하늘과 바람과 별과 시", "셜록 홈즈", "아주 작은 습관의 힘", "이기적 유전자", "죄와 벌",
    "여행의 이유", "넛지", "해리 포터와 마법사의 돌", "나미야 잡화점의 기적", "정의란 무엇인가",
]
anchor = users["test1"]["token"]
books = []
for q in QUERIES:
    b = register_book(anchor, q)
    if b:
        books.append(b)
        print("  %-3s %s" % (b["id"], b["title"][:40]))

if len(books) < 5:
    raise SystemExit("책이 너무 적어 시드를 진행할 수 없다")

# ── 3. 서재 ───────────────────────────────────────────────
print("\n서재")
SHELF = {
    "test1": [(0, "FINISHED", 584, None, 5), (1, "READING", 620, 240, None),
              (2, "WANT_TO_READ", None, None, None), (3, "PAUSED", 200, 60, 3)],
    "test2": [(1, "FINISHED", 620, None, 4), (4, "READING", 636, 300, None),
              (5, "WANT_TO_READ", None, None, None)],
    "test3": [(2, "FINISHED", 264, None, 5), (6, "READING", 719, 120, None),
              (0, "READING", 584, 100, None), (7, "DROPPED", 120, 30, 2)],
    "test4": [(8, "FINISHED", 466, None, 4), (0, "WANT_TO_READ", None, None, None),
              (9, "READING", 750, 410, None)],
    "test5": [(3, "READING", 200, 45, None), (5, "WANT_TO_READ", None, None, None)],

    "yungaram":  [(10, "FINISHED", 120, None, 5), (7, "FINISHED", 110, None, 4),
                  (15, "FINISHED", 200, None, 4), (18, "READING", 420, 70, None)],
    "hanjiwoo":  [(11, "READING", 300, 280, None), (18, "FINISHED", 420, None, 5),
                  (14, "READING", 690, 340, None), (5, "WANT_TO_READ", None, None, None)],
    "seominjae": [(12, "FINISHED", 280, None, 4), (16, "READING", 320, 180, None),
                  (3, "FINISHED", 200, None, 3), (19, "READING", 400, 110, None)],
    "ohsehun":   [(13, "FINISHED", 380, None, 5), (6, "READING", 719, 300, None),
                  (9, "READING", 750, 240, None), (4, "PAUSED", 636, 150, None)],
    "baesua":    [(14, "READING", 690, 350, None), (5, "FINISHED", 336, None, 4),
                  (2, "READING", 264, 90, None), (1, "READING", 620, 70, None)],
    "imhaneul":  [(15, "FINISHED", 200, None, 5), (7, "FINISHED", 110, None, 5),
                  (10, "READING", 120, 70, None), (3, "DROPPED", 200, 100, 2)],
    "kangtaeo":  [(16, "FINISHED", 320, None, 4), (12, "READING", 280, 120, None),
                  (19, "READING", 400, 230, None), (9, "WANT_TO_READ", None, None, None)],
    "moonsori":  [(7, "FINISHED", 110, None, 5), (17, "READING", 330, 220, None),
                  (18, "READING", 420, 100, None)],
    "shinyujin": [(17, "FINISHED", 330, None, 5), (11, "READING", 300, 90, None),
                  (18, "READING", 420, 150, None), (8, "PAUSED", 466, 70, None)],
    "joeunbyul": [(9, "FINISHED", 466, None, 5), (4, "READING", 636, 260, None),
                  (1, "READING", 620, 160, None), (19, "READING", 400, 240, None)],
}
for handle, rows in SHELF.items():
    token = users[handle]["token"]
    added = 0
    for idx, status_name, page_count, current_page, rating in rows:
        if idx < len(books):
            if shelve(token, books[idx]["id"], status_name, page_count, current_page, rating):
                added += 1
    print("  %-6s %d권" % (handle, added))

# ── 4. 감상평 ─────────────────────────────────────────────
print("\n감상평")
# 한 사람이 같은 책을 여러 번에 나눠 읽으며 남긴 기록이 섞이도록 짰다. 길이도 말투도
# 제각각이어야 한다 — 전부 두 줄짜리 단정문이면 사람이 쓴 것처럼 보이지 않는다.
# 한 사람이 같은 책을 여러 번에 나눠 읽으며 남긴 기록이 섞이도록 짰다. 길이도 말투도
# 제각각이어야 한다 — 전부 두 줄짜리 단정문이면 사람이 쓴 것처럼 보이지 않는다.
#
# 100건이 넘는다. 커서 페이징이 여러 쪽 넘어가는 것을 화면에서 봐야 하기 때문이다.
POSTS = [
    # ── 김서연 — 기술서와 소설을 번갈아, 구간을 꼬박꼬박 적는다
    ("test1", 0, "이름 짓기에 이렇게까지 지면을 쓰는 책은 처음이다. 읽고 나서 어제 내가 쓴 변수명을 다시 봤는데 부끄러웠다.", 47, 92, False),
    ("test1", 0, "함수는 한 가지만 해야 한다는 장. 알고는 있었는데 예시를 따라가다 보니 내 함수들이 얼마나 여러 일을 하고 있었는지 보인다.", 93, 140, False),
    ("test1", 0, "주석에 대한 장이 제일 좋았다. 좋은 주석은 코드가 못 하는 말을 한다는 문장에 밑줄.", 141, 178, False),
    ("test1", 1, "1부를 겨우 넘겼다. 인물이 많아서 계보를 그려 가며 읽는 중인데 그 수고가 아깝지 않다.", 1, 240, False),
    ("test1", 3, "초반 100쪽이 유난히 안 넘어간다. 잠깐 덮어 둔다.", 1, 60, False),
    ("test1", 2, "출근길에 30쪽. 오늘은 여기까지.", 1, 30, False),

    # ── 박도윤 — 완독보다 완주. 짧게 자주
    ("test2", 1, "최참판댁이 무너지는 대목에서 한참 멈춰 있었다.", 300, 360, True),
    ("test2", 1, "다 읽었다. 2권도 바로 시작할 것 같다.", None, None, False),
    ("test2", 4, "인류가 밀을 길들인 게 아니라 밀이 인류를 길들였다는 문장에서 멈췄다.", 120, 180, False),
    ("test2", 4, "농업혁명을 사기라고 부르는 대목이 계속 맴돈다.", 181, 300, False),
    ("test2", 5, "표지만 보고 샀는데 생각보다 얇다. 다음 주에 시작.", None, None, False),

    # ── 이하은 — 재독파
    ("test3", 2, "새는 알에서 나오려고 투쟁한다. 고등학생 때 읽었을 때와 완전히 다른 문장으로 읽힌다.", 80, 120, False),
    ("test3", 2, "다 읽었다. 십 년 뒤에 또 읽으면 또 다르게 읽힐 것 같아서 서재에 그냥 둔다.", 121, 264, False),
    ("test3", 0, "동료가 추천해서 읽는 중. 3장까지는 당연한 얘기 같았는데 4장부터 찔린다.", 95, 130, False),
    ("test3", 6, "138억 년을 300쪽으로 줄이면 이런 문장이 되는구나. 자기 전에 조금씩 읽기 좋다.", 60, 120, False),
    ("test3", 7, "30쪽 읽고 접었다. 지금 내 상태로는 안 읽힌다. 나중에.", 1, 30, False),

    # ── 최준우 — 벽돌책. 한 번에 많이, 길게
    ("test4", 8, "상실의 시대라는 제목으로 읽었던 기억이 있는데 다시 읽으니 다른 소설 같다. 그때는 연애 소설로 읽었고 지금은 상실에 대한 이야기로 읽힌다. 같은 문장인데 왜 다르게 읽히는지 모르겠다.", 200, 300, False),
    ("test4", 8, "마지막 장. 결말을 알고 읽는데도 같은 자리에서 멈췄다.", 440, 466, True),
    ("test4", 9, "왜 어떤 대륙은 앞서고 어떤 대륙은 그러지 못했는가. 지리가 답이라는 전개인데, 반쯤 읽은 지금은 설득당하는 중이다.", 300, 410, False),
    ("test4", 0, "팀에서 돌려 읽는 중이라 나도 샀다. 아직 안 폈다.", None, None, False),

    # ── 정민서 — 이제 막 시작
    ("test5", 3, "아직 45쪽. 미움받을 용기라는 제목이 이런 뜻이었나 싶다.", 1, 45, False),
    ("test5", 3, "오늘은 못 읽었다. 내일은 읽어야지.", None, None, False),

    # ── 윤가람 — 시집. 한 편씩
    ("yungaram", 10, "서시만 세 번 읽었다. 아는 시인데 오늘따라 첫 줄에서 막힌다.", 9, 11, False),
    ("yungaram", 10, "별 헤는 밤. 어릴 때 외웠던 것과 다른 부분에 밑줄을 긋게 된다.", 40, 45, False),
    ("yungaram", 10, "시집은 다 읽었다고 말하기가 어렵다. 일단 여기까지.", 1, 120, False),
    ("yungaram", 7, "어른을 위한 동화라는 말을 잘 안 믿었는데, 여우 나오는 장에서 납득했다.", 60, 85, False),
    ("yungaram", 7, "다 읽고 앞장으로 돌아가 헌사를 다시 읽었다.", None, None, False),
    ("yungaram", 15, "여행지에서 읽으려고 샀는데 결국 집에서 다 읽었다.", 1, 90, False),
    ("yungaram", 15, "떠나는 이유보다 돌아오는 이유를 쓴 책에 가깝다.", 91, 200, False),
    ("yungaram", 2, "시만 읽다가 소설을 폈더니 문장이 길게 느껴진다.", 1, 40, False),
    ("yungaram", 18, "한 편씩 끊어 읽기 좋다. 오늘은 두 편.", 1, 70, False),

    # ── 한지우 — 추리
    ("hanjiwoo", 11, "주홍색 연구부터 순서대로. 첫 사건은 생각보다 싱겁다.", 1, 60, False),
    ("hanjiwoo", 11, "왓슨이 화자라서 성립하는 이야기라는 걸 이제야 알겠다.", 61, 140, False),
    ("hanjiwoo", 11, "범인을 알고 봐도 재미있는 종류의 추리가 있다.", 141, 220, True),
    ("hanjiwoo", 11, "오늘 두 편. 자기 전에 한 편씩 읽는 습관이 생겼다.", 221, 280, False),
    ("hanjiwoo", 18, "추리는 아닌데 구조가 추리 같다. 편지가 오가는 방식이 계속 궁금하게 만든다.", 1, 120, False),
    ("hanjiwoo", 18, "마지막 편지에서 결국 울었다.", 300, 420, True),
    ("hanjiwoo", 14, "죄와 벌은 추리소설로도 읽힌다는 말을 듣고 폈다. 아직 100쪽.", 1, 100, False),
    ("hanjiwoo", 14, "심문 장면이 길다. 길어서 좋다.", 260, 340, False),
    ("hanjiwoo", 5, "감시당하는 이야기를 읽는데 요즘 뉴스가 겹쳐 보인다.", 1, 80, False),

    # ── 서민재 — 자기계발. 실천 기록처럼
    ("seominjae", 12, "1퍼센트씩 나아지면 1년에 37배가 된다는 계산. 숫자로 보니 마음이 좀 달라진다.", 20, 55, False),
    ("seominjae", 12, "습관 쌓기를 오늘부터 해 본다. 물 마시기 다음에 책 펴기.", 56, 110, False),
    ("seominjae", 12, "3주째. 아침 독서는 자리 잡았는데 운동은 아직이다.", 111, 180, False),
    ("seominjae", 12, "다 읽었다. 요약하면 환경을 바꾸라는 얘기.", 181, 280, False),
    ("seominjae", 3, "미움받을 용기와 겹치는 얘기가 많다. 둘 다 결국 과제의 분리.", 1, 90, False),
    ("seominjae", 16, "선택을 설계한다는 개념이 신선하다. 구내식당 배치 얘기가 제일 와닿았다.", 30, 95, False),
    ("seominjae", 16, "중반부는 좀 늘어진다. 사례가 반복된다.", 96, 180, False),
    ("seominjae", 19, "정의를 하나로 못 정한다는 게 결론인 것 같아 답답하면서도 정직하다.", 1, 110, False),
    ("seominjae", 0, "개발자 아닌데 팀에서 추천받아 읽는 중. 절반은 모르겠고 절반은 알겠다.", 1, 80, False),

    # ── 오세훈 — 과학
    ("ohsehun", 13, "유전자의 눈으로 보면 우리가 탈것이라는 관점. 처음엔 불쾌했는데 읽을수록 납득된다.", 40, 110, False),
    ("ohsehun", 13, "밈 얘기가 나오는 마지막 장. 이게 1976년 책이라는 게 놀랍다.", 300, 380, False),
    ("ohsehun", 6, "창백한 푸른 점. 아이한테 읽어 줬더니 조용해졌다.", 200, 230, False),
    ("ohsehun", 6, "오늘은 지구 밖 생명 이야기. 잠이 안 온다.", 231, 300, False),
    ("ohsehun", 4, "사피엔스와 겹쳐 읽으니 인간이 유난히 이상한 종이라는 게 더 또렷해진다.", 60, 150, False),
    ("ohsehun", 9, "총 균 쇠는 세 번째 시도인데 이번엔 넘어갈 것 같다.", 1, 90, False),
    ("ohsehun", 9, "가축화 가능한 동물이 몇 종 안 된다는 표. 이 표 하나가 책의 절반이다.", 160, 240, False),
    ("ohsehun", 5, "과학책만 읽다가 소설. 문장이 짧아서 오히려 낯설다.", 1, 60, False),

    # ── 배수아 — 고전. 느리게
    ("baesua", 14, "1부만 2주째. 라스콜니코프의 방이 계속 눈에 밟힌다.", 1, 120, False),
    ("baesua", 14, "노파를 찾아가는 장면. 알고 읽는데도 손에 땀이 난다.", 121, 200, True),
    ("baesua", 14, "소냐가 등장하고 나서 소설의 공기가 바뀐다.", 260, 350, False),
    ("baesua", 14, "에필로그는 급하게 끝나는 느낌인데, 그래서 더 오래 남는다.", 600, 690, True),
    ("baesua", 5, "디스토피아를 고전으로 읽으면 예언처럼 읽힌다.", 80, 160, False),
    ("baesua", 2, "데미안은 청소년기에 읽는 책이라는 말에 동의하지 않게 됐다.", 1, 90, False),
    ("baesua", 8, "일본 소설은 잘 안 읽는데 이건 문장이 붙잡는다.", 1, 110, False),
    ("baesua", 1, "토지는 언젠가 읽어야지 하다가 드디어 폈다. 1권만 한 달 걸릴 듯.", 1, 70, False),

    # ── 임하늘 — 에세이. 밑줄 중심
    ("imhaneul", 15, "여행의 이유가 이렇게 많을 일인가 싶다가도, 읽다 보면 다 내 이유 같다.", 20, 80, False),
    ("imhaneul", 15, "혼자 걷는 대목에서 괜히 코끝이 시큰했다.", 120, 190, False),
    ("imhaneul", 7, "어린 왕자를 어른이 되어 읽으면 여우가 아니라 조종사가 보인다.", 1, 60, False),
    ("imhaneul", 10, "시집은 계절 따라 다르게 읽힌다. 지금은 가을 쪽 시들이 좋다.", 30, 70, False),
    ("imhaneul", 18, "따뜻한 이야기인데 마냥 따뜻하지만은 않다.", 100, 220, False),
    ("imhaneul", 3, "상처받을 용기라고 읽어도 말이 되는 것 같다.", 1, 100, False),
    ("imhaneul", 2, "오늘은 세 쪽. 그래도 폈다는 게 어디인가.", 45, 48, False),

    # ── 강태오 — 경제경영. 요약형
    ("kangtaeo", 16, "요약: 사람은 합리적이지 않다, 그러니 기본값을 잘 설계하라.", 1, 90, False),
    ("kangtaeo", 16, "연금 자동가입 사례. 우리나라에 적용하면 어떻게 될지 궁금하다.", 91, 170, False),
    ("kangtaeo", 12, "습관 책인데 조직에도 그대로 적용된다. 팀 회고에 써먹을 것.", 20, 120, False),
    ("kangtaeo", 19, "샌델의 강의를 글로 옮긴 티가 난다. 좋은 의미로.", 1, 120, False),
    ("kangtaeo", 19, "트롤리 문제는 이제 식상한데, 뒤에 나오는 징병 사례가 훨씬 날카롭다.", 121, 230, False),
    ("kangtaeo", 4, "경제서 읽다가 역사서. 결국 같은 얘기를 하고 있다는 생각이 든다.", 200, 300, False),
    ("kangtaeo", 9, "분량 때문에 미루다가 시작. 하루 40쪽씩 계획.", 1, 40, False),

    # ── 문소리 — 아이와 함께
    ("moonsori", 7, "아이가 잠들기 전에 한 장씩. 오늘은 장미 나오는 데까지.", 20, 35, False),
    ("moonsori", 7, "다 읽었다. 아이는 여우가 제일 좋댄다.", 36, 110, False),
    ("moonsori", 17, "아이보다 내가 더 빠져서 읽는 중이다.", 1, 120, False),
    ("moonsori", 17, "대각선 통로 장면을 두 번 읽어 줬다.", 121, 220, False),
    ("moonsori", 10, "아이가 시를 따라 읽는다. 뜻은 몰라도 소리가 좋은 모양이다.", 8, 20, False),
    ("moonsori", 18, "아이 재우고 혼자 읽는 시간. 오늘은 40쪽.", 60, 100, False),
    ("moonsori", 3, "육아서 대신 이걸 읽는다. 결국 관계 얘기라서.", 1, 80, False),

    # ── 신유진 — 판타지. 밤새우는 편
    ("shinyujin", 17, "새벽 두 시. 딱 한 장만 더 읽자고 했는데 세 장 읽었다.", 1, 140, False),
    ("shinyujin", 17, "퀴디치 경기 장면은 몇 번을 읽어도 재밌다.", 141, 250, False),
    ("shinyujin", 17, "다 읽었다. 내일 2권 사러 간다.", 251, 330, False),
    ("shinyujin", 11, "판타지만 읽다가 추리로. 이것도 결국 세계관 구축이라는 점에서 비슷하다.", 1, 90, False),
    ("shinyujin", 18, "장르는 다른데 밤새우게 만드는 건 똑같다.", 1, 150, False),
    ("shinyujin", 2, "성장소설이라기엔 어둡고, 판타지라기엔 현실적이다.", 1, 100, False),
    ("shinyujin", 8, "친구가 인생책이라길래. 아직은 잘 모르겠다.", 1, 70, False),

    # ── 조은별 — 역사. 연표 그리며
    ("joeunbyul", 9, "1장 읽고 연표를 그렸다. 만 삼천 년을 한 장에 넣으려니 손이 아프다.", 1, 100, False),
    ("joeunbyul", 9, "잉카와 스페인이 만나는 장면. 여기가 이 책의 핵심인 듯.", 101, 200, True),
    ("joeunbyul", 9, "다 읽었다. 결론보다 질문이 좋은 책이다.", 201, 466, False),
    ("joeunbyul", 4, "사피엔스는 총 균 쇠의 후속편처럼 읽힌다.", 1, 120, False),
    ("joeunbyul", 4, "화폐 이야기가 제일 재미있다. 돈은 결국 믿음이라는 것.", 180, 260, False),
    ("joeunbyul", 1, "토지를 역사서처럼 읽는 중이다. 실제 연표와 대조하면서.", 1, 160, False),
    ("joeunbyul", 14, "19세기 러시아를 알고 읽으니 다르게 보인다.", 1, 130, False),
    ("joeunbyul", 19, "정의를 역사적으로 따라가는 장이 제일 좋았다.", 150, 240, False),
]
# 목표 상태로 수렴시킨다. 있어야 할 글은 채우고, 시드 계정이 쓴 글 중 목록에 없는 것은
# 지운다 — 쌓기만 하면 예전 시드와 손으로 넣은 글이 겹겹이 남아 화면이 지저분해진다.
written, skipped, removed = {}, 0, 0
already = {h: existing_posts(u["token"], u["handle"]) for h, u in users.items()}
wanted = {h: set() for h in users}
for handle, idx, content, fp, tp, spoiler in POSTS:
    if idx >= len(books):
        continue
    wanted[handle].add(content)
    if content in already[handle]:
        skipped += 1
        continue
    if write_post(users[handle]["token"], books[idx]["id"], content, fp, tp, spoiler):
        written[handle] = written.get(handle, 0) + 1
for handle, posts in already.items():
    for content, post_id in posts.items():
        if content not in wanted[handle]:
            code, _ = call("DELETE", "/posts/%d" % post_id, token=users[handle]["token"])
            if code == 204:
                removed += 1
for handle in ("test1", "test2", "test3", "test4", "test5"):
    print("  %-6s %d건" % (handle, written.get(handle, 0)))
if skipped:
    print("  (이미 있어 건너뜀 %d건)" % skipped)
if removed:
    print("  (목록에 없어 지움 %d건)" % removed)

# ── 5. 팔로우 ─────────────────────────────────────────────
print("\n팔로우")
# test1~5는 서로를 팔로우한다. 다른 독자 열 명은 아무도 팔로우하지 않는데, 그래야 그들의 글이
# 다섯 명의 홈(= 팔로우하지 않은 사람들의 글)을 채운다 — 페이징이 여러 쪽 넘어가는 것을
# 화면에서 보려면 그 글들이 필요하다.
GRAPH = {
    "test1": ["test2", "test3", "test4"],
    "test2": ["test1", "test3"],
    "test3": ["test1"],
    "test4": ["test1", "test2", "test3", "test5"],
    "test5": [],  # 아무도 팔로우하지 않음 — 빈 팔로잉 피드를 확인하는 계정
    # 독자들끼리도 조금은 이어 둔다. 남의 프로필에서 팔로워 수가 0만 보이면 어색하다.
    "yungaram": ["imhaneul", "baesua"],
    "hanjiwoo": ["shinyujin"],
    "seominjae": ["kangtaeo"],
    "ohsehun": ["joeunbyul"],
    "baesua": ["yungaram"],
    "imhaneul": ["yungaram"],
    "kangtaeo": ["seominjae", "joeunbyul"],
    "moonsori": ["imhaneul"],
    "shinyujin": ["hanjiwoo"],
    "joeunbyul": ["ohsehun", "kangtaeo"],
}
for handle, targets in GRAPH.items():
    token = users[handle]["token"]
    wanted_handles = {users[t]["handle"] for t in targets}
    for t in targets:
        call("POST", "/users/%s/follow" % users[t]["handle"], token=token)
    # 목표에 없는 팔로우는 끊는다. test5가 아무도 팔로우하지 않는 상태여야
    # "팔로잉 피드가 빈 화면"을 확인할 계정이 남는다.
    _, page = call("GET", "/users/%s/followings?size=50" % users[handle]["handle"], token=token)
    dropped = 0
    for item in page["items"]:
        if item["handle"] not in wanted_handles:
            code, _ = call("DELETE", "/users/%s/follow" % item["handle"], token=token)
            if code == 204:
                dropped += 1
    print("  %-6s → %d명%s" % (handle, len(targets), "  (정리 %d)" % dropped if dropped else ""))

# ── 6. 연간 목표 ──────────────────────────────────────────
print("\n연간 목표 (2026)")
for handle, target in (("test1", 24), ("test2", 12), ("test3", 30), ("test4", 12),
                       ("yungaram", 50), ("hanjiwoo", 20), ("seominjae", 36),
                       ("ohsehun", 15), ("baesua", 6), ("joeunbyul", 18)):
    code, goal = call("PUT", "/me/goals/2026", {"targetCount": target}, users[handle]["token"])
    if code == 200:
        print("  %-6s 목표 %-3d 완독 %d" % (handle, goal["targetCount"], goal["finishedCount"]))

# ── 확인 ──────────────────────────────────────────────────
print("\n확인 (test1 기준)")
for label, path in (("홈", "/feed"), ("팔로잉", "/feed/following"),
                    ("내 글", "/users/%s/posts" % users["test1"]["handle"]),
                    ("서재", "/users/%s/library" % users["test1"]["handle"])):
    code, page = call("GET", path, token=users["test1"]["token"])
    print("  %-6s %d건 hasNext=%s" % (label, len(page["items"]), page["hasNext"]))
code, profile = call("GET", "/users/%s" % users["test2"]["handle"], token=users["test1"]["token"])
print("  test2 프로필: 팔로워 %d 팔로잉 %d isFollowing=%s"
      % (profile["followerCount"], profile["followingCount"], profile["isFollowing"]))
code, page = call("GET", "/feed/following", token=users["test5"]["token"])
print("  test5 팔로잉 피드: %d건 (비어 있어야 정상)" % len(page["items"]))
