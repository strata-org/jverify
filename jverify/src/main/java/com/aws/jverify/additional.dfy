const intMax := 4294967296
type nat32 = x: int32 | x >= 0
type int32 = x: int | -4294967296 < x && x < intMax