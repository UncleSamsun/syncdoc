import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// vitest는 globals를 켜지 않으면 자동 정리가 걸리지 않는다.
// 정리하지 않으면 앞 테스트가 그린 DOM이 남아 같은 역할의 요소가 중복된다.
afterEach(() => cleanup());
