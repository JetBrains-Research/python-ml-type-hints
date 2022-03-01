import numpy as np
from typing import Dict

a: Dict[int, int] = dict()

b = 5


def f(t):
    t += "sss"
    return t


f("abc")
