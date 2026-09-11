# -*- coding: utf-8 -*-
"""QA용 시드 데이터.

실행 중인 앱의 **공개 API만** 써서 넣는다 — DB에 직접 쓰지 않는다. 그래서 들어가는 경로가
곧 프런트가 부를 경로이고, 시드가 성공한다는 것 자체가 그 경로들이 살아 있다는 뜻이다.

    docker compose up -d
    .\gradlew.bat bootRun --args='--spring.profiles.active=local'
    python tools/seed-qa.py

주소를 바꾸려면 TWELVEBOOKS_URL 환경변수. 기본은 http://localhost:8080.

**여러 번 돌려도 같은 상태가 된다.** 책 등록은 업서트, 이미 담은 책·이미 한 팔로우는
409를 성공으로 치고, 감상평은 같은 본문이 이미 있으면 건너뛴다.

계정이 없으면 만든다. 비밀번호는 전부 123456789 — 로컬 QA 전용이고 운영에 쓰지 않는다.
"""
import json
import os
import urllib.parse
import urllib.request
import urllib.error

BASE = os.environ.get("TWELVEBOOKS_URL", "http://localhost:8080").rstrip("/") + "/api/v1"
PASSWORD = "123456789"

ACCOUNTS = [
    ("test1@test.com", "test1", "김서연"),
    ("test2@test.com", "test2", "박도윤"),
    ("test3@test.com", "test3", "이하은"),
    ("test4@test.com", "test4", "최준우"),
    ("test5@test.com", "test5", "정민서"),
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


def login_or_signup(email, handle, display_name):
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
    # 프로필 수정은 빈 본문이어도 현재 프로필을 돌려준다 — handle을 여기서 알아낸다.
    _, profile = call("PATCH", "/me", {}, token)
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


def existing_contents(token, handle):
    """이미 쓴 감상평의 본문. 다시 돌렸을 때 같은 글이 또 쌓이지 않게 하려고 모은다."""
    seen, cursor = set(), None
    while True:
        path = "/users/%s/posts?size=50" % handle + ("&cursor=%d" % cursor if cursor else "")
        code, page = call("GET", path, token=token)
        if code != 200:
            return seen
        seen.update(item["content"] for item in page["items"])
        if not page["hasNext"]:
            return seen
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
for email, handle, name in ACCOUNTS:
    token, real_handle = login_or_signup(email, handle, name)
    users[handle] = {"token": token, "handle": real_handle, "name": name}
    print("  %-6s %-8s %s" % (handle, real_handle, name))

# ── 2. 책 ─────────────────────────────────────────────────
print("\n책")
QUERIES = [
    "클린 코드", "토지 박경리", "데미안", "미움받을 용기", "사피엔스",
    "1984 조지 오웰", "코스모스 칼 세이건", "어린 왕자", "노르웨이의 숲", "총 균 쇠",
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
POSTS = [
    ("test1", 0, "47~92쪽. 이름 짓기에 이렇게까지 지면을 쓰는 책은 처음이다. 읽고 나니 내가 어제 쓴 변수명이 부끄러워졌다.", 47, 92, False),
    ("test1", 0, "함수는 한 가지 일만 해야 한다는 장. 알고는 있었는데 예시를 따라가다 보니 내 코드가 얼마나 여러 일을 하고 있었는지 보인다.", 93, 140, False),
    ("test1", 1, "1부를 겨우 넘겼다. 인물이 많아 계보를 그려 가며 읽는 중인데, 그 수고가 아깝지 않다.", 1, 240, False),
    ("test1", 3, "초반 100쪽이 힘들다. 잠시 덮어 둔다. 나중에 다시 펴기로.", 1, 60, False),
    ("test2", 1, "최참판댁이 무너지는 대목. 여기서 한참 멈춰 있었다.", 300, 360, True),
    ("test2", 4, "인류가 밀을 길들인 게 아니라 밀이 인류를 길들였다는 문장에서 한참을 멈췄다.", 120, 180, False),
    ("test2", 4, "농업혁명을 사기라고 부르는 대목이 계속 맴돈다. 오늘은 여기까지.", 181, 300, False),
    ("test3", 2, "새는 알에서 나오려고 투쟁한다. 고등학생 때 읽었을 때와 완전히 다른 문장으로 읽힌다.", 80, 120, False),
    ("test3", 2, "다 읽었다. 십 년 뒤에 또 읽으면 또 다르게 읽힐 것 같아서 서재에 남겨 둔다.", 121, 264, False),
    ("test3", 0, "주석에 대한 장. 좋은 주석은 코드가 못 하는 말을 한다는 부분에 밑줄.", 95, 130, False),
    ("test3", 6, "138억 년을 300쪽으로 줄이면 이런 문장이 되는구나. 밤에 읽기 좋다.", 60, 120, False),
    ("test4", 8, "상실의 시대라는 제목으로 읽었던 기억이 있는데, 다시 읽으니 다른 소설 같다.", 200, 300, False),
    ("test4", 9, "왜 어떤 대륙은 앞서고 어떤 대륙은 그러지 못했는가. 지리가 답이라는 전개.", 300, 410, False),
    ("test4", 8, "마지막 장. 결말을 알고 읽는데도 같은 자리에서 멈췄다.", 440, 466, True),
    ("test5", 3, "아직 45쪽. 미움받을 용기라는 제목이 이런 뜻이었나 싶다.", 1, 45, False),
]
written, skipped = {}, 0
already = {h: existing_contents(u["token"], u["handle"]) for h, u in users.items()}
for handle, idx, content, fp, tp, spoiler in POSTS:
    if idx >= len(books):
        continue
    if content in already[handle]:
        skipped += 1
        continue
    if write_post(users[handle]["token"], books[idx]["id"], content, fp, tp, spoiler):
        written[handle] = written.get(handle, 0) + 1
for handle in ("test1", "test2", "test3", "test4", "test5"):
    print("  %-6s %d건" % (handle, written.get(handle, 0)))
if skipped:
    print("  (이미 있어 건너뜀 %d건)" % skipped)

# ── 5. 팔로우 ─────────────────────────────────────────────
print("\n팔로우")
GRAPH = {
    "test1": ["test2", "test3", "test4"],
    "test2": ["test1", "test3"],
    "test3": ["test1"],
    "test4": ["test1", "test2", "test3", "test5"],
    "test5": [],  # 아무도 팔로우하지 않음 — 빈 팔로잉 피드를 확인하는 계정
}
for handle, targets in GRAPH.items():
    token = users[handle]["token"]
    done = 0
    for t in targets:
        code, _ = call("POST", "/users/%s/follow" % users[t]["handle"], token=token)
        if code in (204, 409):
            done += 1
    print("  %-6s → %d명" % (handle, done))

# ── 6. 연간 목표 ──────────────────────────────────────────
print("\n연간 목표 (2026)")
for handle, target in (("test1", 24), ("test2", 12), ("test3", 30), ("test4", 12)):
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
