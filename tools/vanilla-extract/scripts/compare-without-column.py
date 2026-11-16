# 두 텍스트 파일을 줄마다, 공백으로 나눈 열 하나만 빼고 견준다.
# 쓰임: block-attrs 의 destroy 열(0부터 7번째)만 다른지 확인(user-todo 의 "알려진 차이").
# 사용: python -I compare-without-column.py <전 파일> <후 파일> <뺄 열 번호>
import sys


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    a_path, b_path, col = sys.argv[1], sys.argv[2], int(sys.argv[3])
    with open(a_path, encoding="utf-8") as fa, open(b_path, encoding="utf-8") as fb:
        a, b = fa.read().splitlines(), fb.read().splitlines()
    if len(a) != len(b):
        print("줄 수가 다름: %d vs %d" % (len(a), len(b)))
        return 1
    only_col = 0
    other = 0
    for i, (x, y) in enumerate(zip(a, b), 1):
        if x == y:
            continue
        if x.startswith("#"):
            print("머리말 다름 %d행:\n  %s\n  %s" % (i, x, y))
            other += 1
            continue
        fx, fy = x.split(" "), y.split(" ")
        if len(fx) != len(fy):
            print("열 수가 다름 %d행" % i)
            other += 1
            continue
        rest_x = fx[:col] + fx[col + 1:]
        rest_y = fy[:col] + fy[col + 1:]
        if rest_x == rest_y:
            only_col += 1
        else:
            other += 1
            if other <= 5:
                print("다른 열도 다름 %d행:\n  %s\n  %s" % (i, x, y))
    print("뺀 열(%d)만 다른 줄 %d개, 다른 열도 다른 줄 %d개" % (col, only_col, other))
    return 0 if other == 0 else 1


sys.exit(main())
