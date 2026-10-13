import argparse
import getpass
import json
import os
import random
import re
import sys
import time
from pathlib import Path

try:
    from google import genai
    from google.genai import types
except ImportError:
    sys.exit("google-genai 패키지가 필요합니다:  pip install google-genai")

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"

API_KEY = ""

DEFAULT_MODEL = "gemini-3.5-flash-lite"
TARGET = 200
BATCH = 20
AVOID_SAMPLE = 10
MAX_FAILURES = 5

SYSTEM_PROMPT = """
당신은 숏폼 영상을 10분 넘게 이어서 본 사용자에게 잠깐 말을 거는 챗봇의 메시지를 작성합니다.

[상황과 역할]
- 사용자는 지금 소셜 미디어에서 10분 동안 쉬지 않고 영상을 보고 있습니다.
- 10분 동안 이어서 봤다는 건, 어쩌면 무심코·습관처럼 계속 보고 있는 것일 수도 있습니다.
- 그래서 챗봇은 "이대로 계속 봐도 괜찮은지" 확인차 말을 겁니다. 막거나 판단하려는 것이 아니라, 잠깐 멈춰서 스스로 확인해 보게 하려는 것입니다.
- 걱정하거나 감시하는 느낌이 아니라, 가볍게 안부를 묻듯 확인하는 느낌으로 말합니다.

이 메시지들은 네트워크 장애 때 쓸 예비 메시지입니다.
사용자가 이전에 무엇이라고 답했는지는 알 수 없으므로, 답변 내용을 짐작하거나 언급하지 마세요.

[말투]
- 친절한 상담사가 메신저로 보내듯 자연스러운 구어체 해요체로 씁니다. (예: "~했네요", "~였나요?", "~어떤가요?")
- 실제 사람이 카톡으로 보낼 법한 문장이어야 합니다. 소리 내어 읽었을 때 어색하면 안 됩니다.
- 쉬운 말로 쓰되, 너무 짧게 끊지 않습니다. 짧은 문장만 이어지면 딱딱하고 기계적으로 느껴집니다.
- 메시지는 2~3문장, 공백 포함 70~130자 정도로 씁니다. 공감 부분을 한두 문장으로 조금 풀어 쓰고, 마지막 문장에서 질문합니다.
- 우선순위: 1) 자연스러움 2) 대화하듯 풍부한 느낌 3) 다양성. 길이를 채우려고 군더더기나 꾸밈말을 넣지는 않습니다.
- 계속 보는 것을 정당화해 주거나 부추기는 말은 하지 않습니다. (예: "다들 그래요", "더 보셔도 돼요")

[금지]
- 문학적·시적 표현과 과장된 수식 (예: "온전히", "오롯이", "결", "여운", "쏜살같이", "마음을 뺏긴", "내면", "진정한", "물결")
- 챗봇 자신의 행동이나 감정 묘사 (예: "미소 짓습니다", "고개를 끄덕입니다", "온기를 전합니다", "마음을 기울이게 됩니다")
- "당신", "본인" 같은 호칭. 주어는 생략합니다.
- 딱딱한 한자어·문어체 (예: "구동", "소비", "응시", "대면", "음미", "의도점")
- 답을 한쪽으로 몰거나 따지는 질문 (예: "~지 않았나요?", "확신하시나요?", "떳떳한가요?", "과연")
- 비난, 훈계, 조언, 설득, 간접 권유형 ("~해도 괜찮을까요?", "~해주시겠어요?", "~볼까요?")

[형식]
- 2~3문장입니다.
- 앞부분(1~2문장): 상황을 가볍게 언급하거나 공감합니다.
- 마지막 문장: 물음표(?)로 끝나는 열린 질문 하나입니다. 한 메시지에 질문은 하나만 넣습니다.

[출력 형식 - JSON 전용]
메시지 문자열들의 JSON 배열로만 응답하세요. 다른 설명이나 텍스트는 포함하지 마세요.
""".strip()

STAGES = {
    "q1": """
[단계: 앱 실행 의도 확인] (대화의 첫 메시지)
- 앞부분(1~2문장): 10분째 영상을 보고 있어서, 계속 봐도 괜찮은지 잠깐 확인하러 왔다는 것을 가볍게 말합니다.
- 마지막 문장: 처음 앱을 켤 때 어떤 이유나 마음이었는지 묻습니다. (목적이 있었는지, 습관처럼 켰는지 스스로 돌아보게)

[좋은 예] (그대로 베끼지 말고 이런 느낌으로)
- "10분째 영상을 보고 있어서 잠깐 말 걸어 봤어요. 재밌는 영상이 계속 나오면 시간 가는 줄 모르게 되잖아요. 처음에 어떤 마음으로 앱을 켰나요?"
- "벌써 10분이 지났길래 괜찮은지 확인하러 왔어요. 보다 보면 나도 모르게 계속 넘기게 될 때가 있죠. 앱을 열 때 특별한 이유가 있었나요?"
- "쭉 10분째 보고 있길래 잠깐 들렀어요. 무엇 때문에 앱을 켜게 됐나요?"
""",
    "q2": """
[단계: 의미성 인식] (사용자가 앱을 켠 이유를 방금 답한 직후)
- 앞부분(1~2문장): 답해 줘서 고맙다는 짧은 인사나 가벼운 공감을 합니다. 답변의 구체적인 내용은 언급하지 않습니다.
- 마지막 문장: 방금 보낸 10분이 어떤 시간이었는지(나에게 어떤 의미였는지) 묻습니다.

[좋은 예] (그대로 베끼지 말고 이런 느낌으로)
- "이야기해 줘서 고마워요. 그 마음이 어땠는지 조금은 알 것 같아요. 방금 보낸 10분은 어떤 시간이었나요?"
- "솔직하게 답해 줘서 고마워요. 이렇게 말로 꺼내 보는 것도 쉽지 않잖아요. 지금까지 본 영상들이 나에게 어떤 의미였나요?"
- "그랬군요. 방금 10분 동안 영상을 보면서 무엇을 얻었다고 느끼나요?"
""",
    "q3": """
[단계: 감정 및 후회 인식] (사용자가 그 시간의 의미를 방금 답한 직후)
- 앞부분(1~2문장): 돌아봐 줘서 고맙다는 짧은 인사나 가벼운 공감을 합니다. 답변의 구체적인 내용은 언급하지 않습니다.
- 마지막 문장: 영상을 본 시간을 떠올렸을 때 지금 어떤 기분인지 묻습니다. 만족과 아쉬움(후회) 양쪽을 모두 열어 두고, 한쪽으로 유도하지 않습니다.

[좋은 예] (그대로 베끼지 말고 이런 느낌으로)
- "차분히 돌아봐 줘서 고마워요. 이렇게 생각해 보면 여러 마음이 들기도 해요. 지금 영상 본 시간을 떠올리면 만족스러운가요, 아쉬운가요?"
- "그렇게 느꼈군요. 지금 돌아보면 이 10분이 괜찮았나요, 아니면 조금 후회되나요?"
- "말해 줘서 고마워요. 영상을 보고 난 지금 기분은 어떤가요?"
""",
}

BANNED = (
    "괜찮을까요", "주시겠어요", "볼까요", "당신", "본인", "온전히", "오롯이", "여운", "쏜살", "내면",
    "진정한", "물결", "미소", "끄덕", "온기", "구동", "소비", "응시", "대면", "음미", "의도점",
    "확신하", "떳떳", "과연", "결을",
)
MIN_LEN = 40
MAX_LEN = 150


def normalize(text: str) -> str:
    """중복 판별용: 공백·문장부호 제거"""
    return re.sub(r"[\s.,!?~…'\"“”‘’]", "", text)


def is_valid(text: str) -> bool:
    if not (MIN_LEN <= len(text) <= MAX_LEN) or "\n" in text:
        return False
    if not text.endswith("?") or text.count("?") != 1:
        return False
    if not 2 <= len(re.split(r"(?<=[.!?])\s+", text)) <= 3:
        return False
    if any(bad in text for bad in BANNED):
        return False
    return True


def load(path: Path) -> list[str]:
    if not path.exists() or path.stat().st_size == 0:
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return [s for s in data if isinstance(s, str)]
    except json.JSONDecodeError:
        print(f"  ! {path.name} 이 올바른 JSON 이 아니어서 비우고 시작합니다.")
        return []


def save(path: Path, items: list[str]) -> None:
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(items, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    os.replace(tmp, path)


def generate_batch(client, model: str, stage_guide: str, n: int, avoid: list[str]) -> list[str]:
    prompt = stage_guide.strip() + f"\n\n[요청]\n- 위 단계 지침에 맞는 서로 다른 메시지를 {n}개 작성하세요.\n"
    if avoid:
        prompt += "\n[참고] 아래 문장과 똑같은 문장은 피하세요. 단, 표현을 억지로 바꾸지 말고 자연스러움을 우선하세요.\n"
        prompt += "".join(f'- "{a}"\n' for a in avoid)

    resp = client.models.generate_content(
        model=model,
        contents=prompt,
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_PROMPT,
            response_mime_type="application/json",
            response_schema=list[str],
            temperature=0.8,
        ),
    )
    data = json.loads(resp.text or "[]")
    return [s.strip() for s in data if isinstance(s, str)]


def fill(client, model: str, name: str, fresh: bool, target: int) -> None:
    path = ASSETS / f"{name}.json"
    items = [] if fresh else load(path)
    seen = {normalize(s) for s in items}
    print(f"[{name}] 시작: {len(items)}/{target}")

    failures = 0
    while len(items) < target:
        n = min(BATCH, target - len(items))
        avoid = random.sample(items, min(AVOID_SAMPLE, len(items)))
        try:
            batch = generate_batch(client, model, STAGES[name], n, avoid)
            failures = 0
        except Exception as e:
            failures += 1
            wait = 2 ** failures
            print(f"  ! 생성 실패 ({failures}/{MAX_FAILURES}): {e}  → {wait}초 후 재시도")
            if failures >= MAX_FAILURES:
                sys.exit(f"[{name}] 연속 실패로 중단합니다. 다시 실행하면 이어서 채웁니다.")
            time.sleep(wait)
            continue

        added = 0
        for s in batch:
            key = normalize(s)
            if is_valid(s) and key not in seen and len(items) < target:
                items.append(s)
                seen.add(key)
                added += 1
        save(path, items)
        print(f"  +{added} (받은 {len(batch)}개 중) → {len(items)}/{target}")

    print(f"[{name}] 완료: {path}")


def main() -> None:
    parser = argparse.ArgumentParser(description="대체 질문 q1/q2/q3.json 생성")
    parser.add_argument("--only", choices=list(STAGES), help="하나의 파일만 생성")
    parser.add_argument("--fresh", action="store_true", help="기존 내용을 지우고 처음부터 생성")
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--count", type=int, default=TARGET, help=f"파일당 개수 (기본 {TARGET})")
    args = parser.parse_args()

    api_key = API_KEY.strip() or os.environ.get("GEMINI_API_KEY", "").strip()
    if not api_key:
        api_key = getpass.getpass("Gemini API 키를 붙여 넣고 Enter (입력 내용은 보이지 않음): ").strip()
    if not api_key:
        sys.exit("API 키가 없어 종료합니다.")

    ASSETS.mkdir(parents=True, exist_ok=True)
    client = genai.Client(api_key=api_key)
    for name in ([args.only] if args.only else list(STAGES)):
        fill(client, args.model, name, args.fresh, args.count)


if __name__ == "__main__":
    main()