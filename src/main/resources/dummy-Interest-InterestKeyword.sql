BEGIN;

-- 기존 데이터 초기화 (순서 중요)
TRUNCATE TABLE interest_keywords RESTART IDENTITY CASCADE;
TRUNCATE TABLE interests RESTART IDENTITY CASCADE;

-- interests (고정 UUID)
INSERT INTO interests (id, name)
VALUES ('11111111-aaaa-bbbb-cccc-111111111111', '주식'),
       ('22222222-aaaa-bbbb-cccc-222222222222', '부동산'),
       ('33333333-aaaa-bbbb-cccc-333333333333', 'AI'),
       ('44444444-aaaa-bbbb-cccc-444444444444', '스포츠'),
       ('55555555-aaaa-bbbb-cccc-555555555555', '정치'),
       ('66666666-aaaa-bbbb-cccc-666666666666', '자동차');

-- interest_keywords (랜덤 UUID 일부 변경)
INSERT INTO interest_keywords (id, name, interest_id)
VALUES ('8a9c2a10-1d7e-4f7e-9a01-000000000001', '삼성전자', '11111111-aaaa-bbbb-cccc-111111111111'),
       ('8a9c2a10-1d7e-4f7e-9a01-000000000002', '코스피', '11111111-aaaa-bbbb-cccc-111111111111'),
       ('8a9c2a10-1d7e-4f7e-9a01-000000000003', 'ETF', '11111111-aaaa-bbbb-cccc-111111111111'),
       ('8a9c2a10-1d7e-4f7e-9a01-000000000004', '환율', '11111111-aaaa-bbbb-cccc-111111111111'),

       ('8a9c2a10-1d7e-4f7e-9a01-000000000005', '아파트', '22222222-aaaa-bbbb-cccc-222222222222'),
       ('8a9c2a10-1d7e-4f7e-9a01-000000000006', '전세', '22222222-aaaa-bbbb-cccc-222222222222'),
       ('8a9c2a10-1d7e-4f7e-9a01-000000000007', '분양', '22222222-aaaa-bbbb-cccc-222222222222'),

       ('8a9c2a10-1d7e-4f7e-9a01-000000000008', '딥러닝', '33333333-aaaa-bbbb-cccc-333333333333'),
       ('8a9c2a10-1d7e-4f7e-9a01-000000000009', '오픈AI', '33333333-aaaa-bbbb-cccc-333333333333'),
       ('8a9c2a10-1d7e-4f7e-9a01-00000000000a', '로봇', '33333333-aaaa-bbbb-cccc-333333333333'),
       ('8a9c2a10-1d7e-4f7e-9a01-00000000000b', '챗GPT', '33333333-aaaa-bbbb-cccc-333333333333'),

       ('8a9c2a10-1d7e-4f7e-9a01-00000000000c', 'KBO', '44444444-aaaa-bbbb-cccc-444444444444'),
       ('8a9c2a10-1d7e-4f7e-9a01-00000000000d', '야구', '44444444-aaaa-bbbb-cccc-444444444444'),
       ('8a9c2a10-1d7e-4f7e-9a01-00000000000e', '프리미어리그', '44444444-aaaa-bbbb-cccc-444444444444'),
       ('8a9c2a10-1d7e-4f7e-9a01-00000000000f', '손흥민', '44444444-aaaa-bbbb-cccc-444444444444'),

       ('8a9c2a10-1d7e-4f7e-9a01-000000000010', '국회', '55555555-aaaa-bbbb-cccc-555555555555'),
       ('8a9c2a10-1d7e-4f7e-9a01-000000000011', '대선', '55555555-aaaa-bbbb-cccc-555555555555'),

       ('8a9c2a10-1d7e-4f7e-9a01-000000000012', '전기차', '66666666-aaaa-bbbb-cccc-666666666666'),
       ('8a9c2a10-1d7e-4f7e-9a01-000000000013', '자율주행', '66666666-aaaa-bbbb-cccc-666666666666'),
       ('8a9c2a10-1d7e-4f7e-9a01-000000000014', '배터리', '66666666-aaaa-bbbb-cccc-666666666666');

COMMIT;
