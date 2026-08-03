/*
 Navicat Premium Dump SQL

 Source Server         : localhost_8.0
 Source Server Type    : MySQL
 Source Server Version : 80036 (8.0.36)
 Source Host           : localhost:3306
 Source Schema         : ai_agent

 Target Server Type    : MySQL
 Target Server Version : 80036 (8.0.36)
 File Encoding         : 65001

 Date: 29/05/2026 00:14:12
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_async_task
-- ----------------------------
DROP TABLE IF EXISTS `ai_async_task`;
CREATE TABLE `ai_async_task`  (
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `request_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `owner_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `owner_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `request_payload` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `result_payload` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `error_message` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `cancel_reason` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `created_at` datetime NOT NULL,
  `started_at` datetime NULL DEFAULT NULL,
  `finished_at` datetime NULL DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`task_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_async_task
-- ----------------------------

-- ----------------------------
-- Table structure for api_key_account
-- ----------------------------
DROP TABLE IF EXISTS `api_key_account`;
CREATE TABLE `api_key_account`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `api_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `owner` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `quota` int NOT NULL DEFAULT 10000,
  `used_count` int NOT NULL DEFAULT 0,
  `expired_at` datetime NULL DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `api_key`(`api_key` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of api_key_account
-- ----------------------------
INSERT INTO `api_key_account` VALUES (1, 'demo-local-key', 'seed', 'active', 100000, 25, NULL, '2026-04-29 16:33:19');

-- ----------------------------
-- Table structure for api_key_usage
-- ----------------------------
DROP TABLE IF EXISTS `api_key_usage`;
CREATE TABLE `api_key_usage`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `api_key_id` bigint NOT NULL,
  `request_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `uri` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `method` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` int NOT NULL,
  `cost_ms` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 26 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of api_key_usage
-- ----------------------------
INSERT INTO `api_key_usage` VALUES (14, 1, NULL, '/api/chat/sessions', 'GET', 200, 166, '2026-05-05 18:19:25');
INSERT INTO `api_key_usage` VALUES (15, 1, NULL, '/api/chat/sessions', 'POST', 200, 24, '2026-05-05 18:19:25');
INSERT INTO `api_key_usage` VALUES (16, 1, NULL, '/api/auth/login', 'POST', 200, 96, '2026-05-05 18:19:45');
INSERT INTO `api_key_usage` VALUES (17, 1, NULL, '/api/auth/login', 'POST', 200, 129, '2026-05-23 20:56:16');
INSERT INTO `api_key_usage` VALUES (18, 1, NULL, '/api/auth/login', 'POST', 200, 77, '2026-05-23 20:56:38');
INSERT INTO `api_key_usage` VALUES (19, 1, NULL, '/api/auth/login', 'POST', 200, 75, '2026-05-23 20:56:39');
INSERT INTO `api_key_usage` VALUES (20, 1, NULL, '/api/auth/login', 'POST', 200, 86, '2026-05-23 20:56:44');
INSERT INTO `api_key_usage` VALUES (21, 1, NULL, '/api/auth/login', 'POST', 200, 79, '2026-05-24 15:48:47');
INSERT INTO `api_key_usage` VALUES (22, 1, NULL, '/api/auth/login', 'POST', 200, 142, '2026-05-24 15:49:00');
INSERT INTO `api_key_usage` VALUES (23, 1, NULL, '/api/chat/sessions', 'GET', 200, 26, '2026-05-28 21:22:05');
INSERT INTO `api_key_usage` VALUES (24, 1, NULL, '/api/chat/sessions', 'POST', 200, 33, '2026-05-28 21:22:06');
INSERT INTO `api_key_usage` VALUES (25, 1, NULL, '/api/auth/login', 'POST', 200, 89, '2026-05-28 21:22:19');

-- ----------------------------
-- Table structure for chat_message
-- ----------------------------
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user_id` bigint NULL DEFAULT NULL,
  `role` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `event_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'answer',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `metadata_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `meta` json NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session_created`(`session_id` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 194 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of chat_message
-- ----------------------------
INSERT INTO `chat_message` VALUES (184, '59bfcda8-768c-4489-ac63-0e1bb1834cbf', 1, 'user', 'question', '我现在一边纠结因嘴馋有点想吃点东西，一边想要早睡调整作息，我怎么选择？', NULL, NULL, '2026-05-23 23:37:57');
INSERT INTO `chat_message` VALUES (185, '59bfcda8-768c-4489-ac63-0e1bb1834cbf', 1, 'assistant', 'answer', '你此刻的纠结，其实是身体信号（饥饿/嘴馋）和长期目标（健康作息）在温和博弈——这恰恰说明你在觉察自己，已经赢了一半✅\n**结论：优先早睡，但可以「聪明地」满足嘴馋，不牺牲睡眠质量。**\n###🌙 分步行动建议（5分钟内可完成）：\n1. **先做30秒呼吸暂停判断**闭眼，深呼吸3次（吸气4秒→屏息2秒→呼气6秒），然后问自己→ 是胃真饿（轻微咕噜、乏力感）？还是嘴馋（想到某食物画面/味道就兴奋）？→ 如果是后者，大概率是压力、无聊或习惯性进食（尤其晚上9点后，皮质醇+褪黑素变化会放大渴望）。\n2. **如果真饿（尤其距晚餐＞4小时）→吃「助眠小食」**（选1种，≤150kcal）：•一小把原味杏仁（6–8颗）＋一杯温热洋甘菊茶• 半根香蕉＋一小勺无糖花生酱（用勺子直接吃，避免开包装引发过量）• 无糖酸奶50g +少许奇亚籽（镇静色氨酸+镁，助褪黑素合成）\n3. **如果只是嘴馋 →启动「延迟满足仪式」**：⏳ 设定手机倒计时15分钟，期间喝一杯温水（常误把口渴当饥饿）用薄荷味牙膏刷牙（味觉重置，降低进食欲）- 打开备忘录，快速写下：“我现在想吃____，是因为我感到____（累/闷/焦虑/空虚）？”\n→15分钟后，80%的人欲望已自然消退；若仍有强烈冲动，再吃上面推荐的小食。\n4. **立刻为早睡铺路**• 关掉所有蓝光屏幕（手机调至灰度模式或开启「睡眠模式」）•调低室温至24℃左右（核心体温下降是入睡关键信号）•播放白噪音/雨声（推荐「潮汐APP」免费「森林夜露」音景）\n•把手机放在卧室外充电（物理隔离最有效）### ⚠️风险提醒：- ❌避免精制碳水/高糖零食（蛋糕、饼干、冰淇淋）：升糖→胰岛素飙升→夜间易醒+凌晨饥饿- ❌ 避免咖啡因/浓茶/巧克力：腺苷受体被阻断，即使困也难进入深睡- ❌别“吃完再睡”：胃需2–3小时排空，睡前1.5小时进食易致胃食管反流、浅睡多梦### 🔁 下一步复盘（明天晨起花1分钟）：> “昨晚我选择____，身体醒来感觉____（清爽/疲惫/口干/头沉）；如果重来，我会在____时刻做一点点不同。”\n需要的话，我可以为你定制一份「7天渐进式晚安启动计划」（含嘴馋替代清单+睡前15分钟微习惯模板），或者帮你分析最近作息/饮食日志找突破口。你更想先聚焦哪一块？ 😴🍎', NULL, NULL, '2026-05-23 23:38:15');
INSERT INTO `chat_message` VALUES (186, '59bfcda8-768c-4489-ac63-0e1bb1834cbf', 1, 'user', 'question', '我现在睡醒了，庆幸昨晚没有在睡前进食。比预期好一些，不过仍然觉得状态一般般，我应该怎么调整开启我新的一天？', NULL, NULL, '2026-05-24 13:29:58');
INSERT INTO `chat_message` VALUES (187, '59bfcda8-768c-4489-ac63-0e1bb1834cbf', 1, 'assistant', 'answer', '#晨间状态调整指南：轻启动·稳节奏·提能量\n##一、目标理解- 核心需求：从低唤醒状态平稳过渡到清醒专注，不依赖强刺激，避免午后崩塌时间窗口：当前至中午12:00，约3–4小时- 预算约束：零花费，仅需现有物品与15分钟以上空闲- 附加条件：尊重身体轻微倦怠感｜拒绝咖啡因/高糖依赖｜兼顾心理轻盈感\n## 二、约束与风险生理风险：空腹过久可能引发低血糖反应（手抖、心慌）｜晨间皮质醇自然高峰未达峰值，强行“打鸡血”易耗竭节奏风险：立即投入高强度任务→注意力涣散→自我否定循环- 环境风险：室内静态久坐→血流减缓→脑雾加重｜光线不足→褪黑素残留→困意反复## 三、计划策略\n- 时间策略：前15分钟「身体优先」｜中间30分钟「感官唤醒」｜后续「小步推进」路线策略：由下而上激活（足→腹→胸→头），避免从大脑开始硬拉氛围策略：用「可触摸的确定性」替代「待办清单压迫感」（如一杯温水的温度、阳光落点的位置）备份策略：若15分钟后仍沉重，启动「微光模式」——只做1件触手可及的事（如开窗、整理桌面一角）\n##四、具体方案\n###方案一：首选（光照锚定法）- 动线：赤脚踩地（30秒）→ 喝200ml温水（静坐3分钟）→靠窗/户外接受自然光照射（5分钟，闭眼亦可）→拉伸肩颈（4分钟）→写下今日1件「最小完成项」（如：回1条消息｜煮一餐饭）亮点：利用晨光重置生物钟，温水激活迷走神经，赤脚接地缓解皮质醇滞留> 风险提示：若阴天，改用台灯（色温5000K+）直射面部3分钟替代；避免手机蓝光干扰- 便利性：无需预约｜无交通成本｜全程居家可完成推荐地点说明：- **窗边光斑区**：选择家中最明亮的窗台，让光线落在手背或额头，不直视太阳- **厨房水龙头旁**：作为温水获取与静坐锚点，减少决策消耗\n###方案二：备选（触觉唤醒法）\n- 动线：用凉水轻拍手腕内侧（10秒）→拿起任意纹理清晰物（木梳/粗陶杯/毛毯）缓慢摩挲30秒 →听一段无歌词白噪音（雨声/火炉声，2分钟）→ 做3次「4-7-8呼吸」（吸气4秒｜屏息7秒｜呼气8秒）\n> 亮点：通过冷刺激+触觉输入快速提升交感神经警觉度，白噪音屏蔽环境杂音降低认知负荷> 风险提示：避免冰水刺激；若呼吸不适，改为自然深呼吸（手放腹部感受起伏）\n- 便利性：所有物品家中常备｜单次耗时≤12分钟｜可碎片化执行推荐地点说明：- **浴室水槽前**：凉水触发即刻清醒反射\n- **沙发/床沿**：作为触觉与呼吸练习的安全落点，无需起身## 五、复盘与下一步本轮完成标准：1. 身体有明确「温度感」或「轻盈感」反馈（非亢奋）\n2.能清晰说出接下来1小时内想做的1件事（不求宏大，但求具体）下一步可执行项：- 完成方案一/二中任一动线后，立刻将「最小完成项」写在便签贴于视线正前方中午12:00前补充含优质脂肪+蛋白的早餐（如：鸡蛋+牛油果｜坚果+无糖酸奶）\n- 地点速览（便于配图，每条独立一行）：- **窗边光斑区**：晨光温柔漫射的物理锚点- **厨房水龙头旁**：温水与静默共存的生活切口- **浴室水槽前**：冷感触发的清醒开关- **沙发/床沿**：触觉与呼吸的安全着陆带> 可点击右上角“导出最近计划PDF”一键导出本次计划。', NULL, NULL, '2026-05-24 13:30:22');
INSERT INTO `chat_message` VALUES (188, '59bfcda8-768c-4489-ac63-0e1bb1834cbf', 1, 'user', 'question', '给我推荐几个适合放空的地方，配上相关的图片', NULL, NULL, '2026-05-24 13:44:48');
INSERT INTO `chat_message` VALUES (189, '59bfcda8-768c-4489-ac63-0e1bb1834cbf', 1, 'assistant', 'answer', '我无法直接生成或发送图片（受限于当前平台功能），但我可以为你**精准描述适合放空的场所场景**，并提供**可一键复制的AI绘图提示词（Prompt）**——你只需将这些提示词粘贴到任意支持文生图的工具中（如 DALL·E、MidJourney、通义万相、即梦、Leonardo.AI 等），即可生成高质量、氛围感强的实景风格图片。\n以下推荐 **4个真实存在、心理友好、低刺激、易抵达的放空型空间**，每个都附带：✅ 场景核心特质（为什么能真正帮你「清空」）\n✅适配人群与时段建议\n✅可直接使用的中文+英文双语绘图提示词（已优化构图/光影/情绪关键词，拒绝空洞风景）✅ 小贴士：如何用5分钟进入状态（不依赖拍照或打卡）\n###1. 晨光中的老式公共图书馆窗边座位**📍为什么放空？**\n安静但不孤寂｜有他人专注的“背景能量”｜自然光柔和漫射｜纸张微香+翻页声构成白噪音｜无手机信号弱区天然存在\n**⏰最佳时段**：工作日上午9:30–11:00（人少、光线斜入）\n**👥适合你**：需要温和启动大脑、抗拒独处却怕社交消耗的人\n**🎨 绘图提示词（中英双语，可直接复制）：**中文：清晨阳光透过高大老图书馆木格窗洒在旧橡木长椅上，一位背影安静坐着，桌上摊开一本翻开的纸质书和一杯热茶，暖调胶片质感，柔焦，轻微尘埃光斑，宁静而有呼吸感，空旷但不冷清 —ar 16:9 —v 6.0> English: Softmorning light streaming through tall woodenlattice windows of a historic publiclibrary, illuminating anempty oak bench witha quietly seated person’s back, openpaperback and steaming teacup on the table,warm film grain texture, shallow depth offield, floating dust particlesin light beams, sereneyet breathable atmosphere, spaciousbut not empty —ar16:9 —v6.0\n💡*放空小动作*：坐下后先闭眼3次深呼吸，再只做一件事——观察光斑在书页上缓慢移动的轨迹（约2分钟），不计时、不评判。\n###2.城市社区小公园的弧形长椅（背靠银杏树）**📍为什么放空？**\n尺度亲切｜有自然节律（风动叶响/鸟鸣间隔）｜无人注视的“半隐蔽感”｜长椅弧度托住腰背，生理上放松\n**⏰最佳时段**：工作日午后14:00–15:30（老人散步少，孩子未放学，光影斜长）\n**👥适合你**：身体疲惫但脑子停不下来，需要“被自然轻轻接住”的人**🎨 绘图提示词：**中文：秋日下午，城市社区小公园，一张浅灰色混凝土弧形长椅半嵌入草地，背后是一棵枝干舒展的银杏树，金黄叶片飘落中，长椅上空着，一只麻雀停在扶手上，柔和侧逆光，低饱和度水彩风格，安宁而不萧瑟 —ar 4:3 —styleraw\n> English: Autumnafternoon in a quieturban neighborhood park, asoft-gray curved concrete benchnestled into grass, backedby a graceful ginkgo tree withgolden leaves mid-fall, one sparrow perched on thearmrest, gentleside-backlighting, low-saturation watercolor aesthetic, peaceful—not melancholy—ar 4:3—style raw\n💡*放空小动作*：把手掌平放在长椅冰凉的混凝土扶手上，专注感受3种触感：温度、颗粒粗细、微小震动（如有风或远处车流），持续90秒。### 3.旧厂房改造的独立咖啡馆角落卡座（非网红款）\n**📍为什么放空？**工业感带来安全感（裸露砖墙/金属梁）｜背景是低频咖啡机蒸汽声+手冲滴滤声｜菜单简单、无需决策压力｜老板不主动搭话的文化默契\n**⏰最佳时段**：周二/三上午10:00–11:30（避开周末人流与周一疲惫潮）\n**👥适合你**：需要“安全结界感”，想待着却不想要“必须产出”的人**🎨 绘图提示词：**中文：非网红风格的老厂房咖啡馆角落，红砖墙+黑色金属横梁，一张深绿色丝绒卡座嵌在窗边凹位，桌上只有一杯手冲咖啡和翻开的素描本（未画任何线条），窗外是模糊的梧桐树影，暖灰主色调，电影静帧感，松弛而有厚度 —ar 2:3—no people\n> English: Unpretentiouscafé in a repurposed factory:exposed red brick wall andblack steel beams, adeep-green velvet boothtucked into a windownook, minimalisttable with only a pour-over coffee cup and anopen blank sketchbook,soft out-of-focus planetree shadows outside thewindow, warm-gray colorpalette, cinematic still frame, relaxed yet substantial—ar 2:3 —no people💡 *放空小动作*：点单时只要一句“今天请给我一杯最慢的手冲”，然后全程观察咖啡师注水节奏——水流、液面升幅、气泡破裂速度，让注意力锚定在“过程”而非“结果”。\n###4.河滨步道尽头的观景水泥墩（非景点，本地人知道）\n**📍为什么放空？**水平线天然镇定视觉系统｜水流声具40Hzα脑波诱导效应｜墩体微震传递水流动能｜“坐在这里=暂时不属于任何角色”\n**⏰最佳时段**：工作日清晨6:45–7:20（晨练人刚散，水面反光最稳）\n**👥适合你**：长期多任务切换、自我感稀薄、需要物理性“重置坐标”的人\n**🎨 绘图提示词：**\n> 中文：清晨河滨步道尽头，一个低矮的灰色水泥观景墩静立水边，墩面微湿反光，远处水波平缓延展至雾中，一只白鹭掠过水面，冷调青灰氛围，广角微俯视角，空灵但不空洞，留白呼吸感强 —ar21:9—style realistic\n> English: Early morning riverside path ending at a lowgray concrete viewing plinth beside calm water, dampsurface reflecting soft sky, gentle ripples stretchinginto misty distance, a white egretgliding low overwater, cool desaturated teal-gray palette, wide-angle slight high angle, ethereal yetgrounded, strong senseof breathing space —ar21:9 —stylerealistic\n💡 *放空小动作*：坐下后把手机倒扣在墩面，掌心朝上平放膝盖，只做一件事——数自己呼吸时，胸口与小腹的起伏是否同步（不调整，只观察），直到发现一次“不同步”即停止。✅ **下一步行动建议（现在就能做）：**\n1️⃣ 选一个最让你心头一松的地点（不用理性分析，凭身体直觉）\n2️⃣复制对应提示词 → 粘贴进你常用的AI绘图工具 → 生成1张图\n3️⃣把这张图设为手机锁屏壁纸（不是桌面！是锁屏）→ 每次亮屏，它就是你的「心理地标」\n需要我帮你：\n🔹优化某个提示词以适配你所在城市的真实地貌（比如杭州/成都/深圳）？\n🔹把其中1个地点转化为「15分钟线下行动清单」（含交通/穿着/携带物提醒）？🔹 或生成配套的「放空语音引导脚本」（3分钟冥想音频文字版）？\n请告诉我你的选择，我立刻为你深化。🌿', NULL, NULL, '2026-05-24 13:45:29');
INSERT INTO `chat_message` VALUES (190, '59bfcda8-768c-4489-ac63-0e1bb1834cbf', 1, 'user', 'question', '我现在在深圳市罗湖区，想跟对象去约会，推荐几个合适的约会地点。适当配上图片', NULL, NULL, '2026-05-28 21:26:20');
INSERT INTO `chat_message` VALUES (191, '59bfcda8-768c-4489-ac63-0e1bb1834cbf', 1, 'assistant', 'answer', '行程规划报告：深圳罗湖浪漫约会精选一、目标理解核心需求：情侣在罗湖区内的轻松、氛围感强、适合互动与拍照的约会体验\n- 时间窗口：当日可启动，建议傍晚至夜间（17:00–22:00），约5小时预算约束：人均¥150–200，总预算¥400内（含交通、餐饮、小消费）\n- 附加条件：步行或短途地铁/打车可达；避免拥挤打卡点；兼顾私密性与城市烟火气二、约束与风险- 人流风险：东门老街周末晚高峰（19:00–20:30）人流量大，影响散步与交谈节奏- 时间风险：部分餐厅需提前1–2小时预约（如“金翠园”粤式茶餐厅）； rooftop酒吧位紧俏安全风险：沿深圳河夜行步道照明良好，但个别桥下段较暗，建议结伴不离主路- 预算风险：国贸商圈高端咖啡馆+小酒馆组合易超支，需提前锁定人均¥80以内选项## 三、计划策略\n- 时间策略：前缓后暖——以轻食/咖啡开启，中段漫步观景，尾声微醺收束路线策略：闭环动线，全程控制在1.5公里内，以地铁1号线“国贸站”为圆心辐射- 氛围策略：融合“老城肌理+江畔光影+在地烟火”，避开纯商业体，强化共同记忆点（如手作、共饮、俯瞰河景）\n- 备份策略：若遇雨天，启用室内备选链：金光华广场L3「隅田川咖啡实验室」→国贸大厦B座「TheNest屋顶花园（室内玻璃穹顶区）」\n## 四、具体方案### 方案一：首选（江畔慢光·罗湖双时区）动线：金光华广场1F「隅田川快闪咖啡角」（17:00–17:45）→ 深圳河滨水步道（春风路至嘉宾路段，18:00–19:15）→国贸大厦B座「The Nest rooftop bar」（19:30–21:30）\n> 亮点：从市井咖啡香到河面波光再到城市天际线微醺时刻，完成空间升维；步道沿途有“深港同心林”灯柱与涂鸦墙，自然形成合影节点> 风险提示：The Nest需现场扫码排队（约15分钟）或提前小程序预约“落日席位”；建议19:00前抵达国贸大厦取号\n- 便利性：金光华与国贸站A口直连；滨水步道入口距地铁口步行3分钟；全程步行+短驳，无换乘压力- 推荐地点说明：\n- 隅田川快闪咖啡角：金光华广场1层中庭，限定款“荔枝乌龙冷萃”配手作拉花，¥28/杯，提供双人竹编托盘，适合初阶互动- 深圳河滨水步道（春风路–嘉宾路）：临河亲水平台设休憩木椅与暖光地灯，可远眺文锦渡口岸灯光，偶有游船驶过；免费开放，18:30后蓝调时刻最佳- The Nest rooftopbar：国贸大厦B座28F，360°环视罗湖CBD+梧桐山轮廓，特调“罗湖夕照”（西柚金酒+接骨木）¥68，含一张双人合影即时打印券方案二：备选（东门烟火·怀旧共生计划）动线：东门町美食街「阿婆牛杂」（17:30–18:15）→老街博物馆（18:30–19:00）→江南新村骑楼巷（19:15–20:30）→ 「LINLEE·手打柠檬茶（东门旗舰店）」露台位（20:45–22:00）亮点：用食物锚定情绪（热牛杂暖胃）、用历史建立共鸣（博物馆影像墙可扫码听80年代罗湖录音）、用建筑细节制造惊喜（骑楼拱廊光影+手写店招）风险提示：东门町周末排队较长，建议错峰或改选“阿婆牛杂”隔壁巷内同源档口“牛记”\n- 便利性：全程位于东门步行街核心区，地铁1/3号线“老街站”C口出即达；江南新村距东门仅500米，穿巷步行更富探索感推荐地点说明：阿婆牛杂（东门町店）：炭炉现炖牛杂配萝卜，¥25/碗，支持加蛋加面；双人份可共享一碗+一份炸物拼盘，烟火气十足\n- 老街博物馆：免费预约制（微信搜“罗湖老街博物馆”），藏有1980年代东门集市胶片与电子互动屏，预留20分钟足够沉浸江南新村骑楼巷：深圳现存最完整骑楼群之一，红砖拱廊+绿植垂挂，傍晚侧光下极具电影感；无门票，自由进出LINLEE露台位：东门旗舰店二楼开放式露台，正对骑楼群，柠檬茶¥18起，“满杯红柚”可选双杯套组赠情侣手环，21:00后亮灯更佳## 五、复盘与下一步本轮完成标准1. 提供两个地理集中、预算可控、氛围分层的罗湖本地约会动线2.明确各环节时间卡点、预约方式与风险规避动作- 下一步可执行项：立即微信搜索“The Nest深圳”小程序预约今晚“落日席位”截图保存「老街博物馆」预约二维码备用- 打开地图APP收藏“江南新村骑楼巷”定位（导航搜“江南新村社区工作站”最近）\n- 地点速览（便于配图，每条独立一行）：\n- 隅田川快闪咖啡角：金光华中庭竹编风快闪，荔枝乌龙冷萃拉花特写\n- 深圳河滨水步道：蓝调时刻河面倒映霓虹，情侣倚栏剪影- The Nest rooftopbar：28F玻璃围栏外梧桐山轮廓与罗湖天际线同框- 阿婆牛杂（东门町）：炭炉牛杂锅蒸汽升腾，青花碗盛装特写- 老街博物馆影像墙：泛黄胶片投影与年轻情侣驻足观看叠化- 江南新村骑楼巷：夕阳斜射红砖拱廊，藤蔓与手写店招虚化前景### 地点配图\n- 核心需求（实景参考） 核心需求 的实景氛围参考，适合作为行动计划的视觉锚点。\n- 时间窗口（实景参考） 时间窗口 的实景氛围参考，适合作为行动计划的视觉锚点。\n- 预算约束（实景参考） 预算约束 的实景氛围参考，适合作为行动计划的视觉锚点。\n- 附加条件（实景参考） 附加条件 的实景氛围参考，适合作为行动计划的视觉锚点。\n- 人流风险（实景参考） 人流风险 的实景氛围参考，适合作为行动计划的视觉锚点。\n- 时间风险（实景参考） 时间风险 的实景氛围参考，适合作为行动计划的视觉锚点。', '{\"images\":[\"https://images.pexels.com/photos/11153407/pexels-photo-11153407.jpeg?auto=compress&cs=tinysrgb&h=350\",\"https://images.pexels.com/photos/32906157/pexels-photo-32906157.jpeg?auto=compress&cs=tinysrgb&h=350\",\"https://images.pexels.com/photos/18501020/pexels-photo-18501020.jpeg?auto=compress&cs=tinysrgb&h=350\",\"https://images.pexels.com/photos/11336600/pexels-photo-11336600.jpeg?auto=compress&cs=tinysrgb&h=350\",\"https://images.pexels.com/photos/33968409/pexels-photo-33968409.jpeg?auto=compress&cs=tinysrgb&h=350\"],\"provider\":\"pexels\"}', NULL, '2026-05-28 21:27:04');
INSERT INTO `chat_message` VALUES (192, '8360f4d9-30ef-426d-a2d1-db8f6ffdb606', 1, 'user', 'question', '我现在感觉很焦虑，就业压力很大，一边想娱乐，一边想学习，我应该怎么做？', NULL, NULL, '2026-05-28 21:29:10');
INSERT INTO `chat_message` VALUES (193, '8360f4d9-30ef-426d-a2d1-db8f6ffdb606', 1, 'assistant', 'answer', '心理调适与行动平衡指南：缓解就业焦虑的双轨策略一、目标理解核心需求：在就业压力下重建心理稳定性，同步推进能力提升与情绪恢复\n- 时间窗口：当前至未来3个月，每日可支配时间约2–4小时预算约束：零新增支出优先，善用免费/低成本资源\n- 附加条件：需避免自我消耗型“假努力”，强调可感知进展与即时反馈\n## 二、约束与风险心理风险：过度切换导致注意力碎片化，加剧无力感行动风险：学习内容脱离求职实际需求，娱乐沦为逃避性刷屏时间风险：未设定边界导致“学不进、玩不尽兴”的恶性循环认知风险：将“焦虑”等同于“不够努力”，忽视其作为信号的提示价值三、计划策略时间策略：采用90分钟「专注块」+15分钟「恢复块」节奏，每日最多2个专注块- 能量策略：优先安排高唤醒活动（如运动、社交）于焦虑峰值时段（常为晨起/睡前1小时）双轨绑定策略：将娱乐与学习物理联结——例如看行业纪录片（娱乐）→ 拆解1个岗位JD（学习）→模拟写1段匹配描述（产出）\n- 验证策略：以“本周是否产出1份可发给真实人的成果”为周度验收标准，而非学习时长## 四、具体方案### 方案一：首选（锚点日计划）\n- 动线：晨间15分钟「焦虑翻译」（纸笔写下3条具体担忧→对应1条可操作小动作）→ 上午90分钟「求职微行动」（如优化简历1模块/投递2家目标公司）→午间30分钟「主题娱乐」（行业播客/职场剧1集）→ 下午15分钟「体感重置」（快走/拉伸/深呼吸）亮点：把抽象焦虑转化为具象动作，娱乐内容直接反哺求职认知\n> 风险提示：若晨间无法启动，跳过翻译直接执行微行动——行动本身即降焦- 便利性：无需预约；所有工具免费（Notion模板/小红书求职话题/央视纪录频道）推荐地点说明：- 小红书「求职避坑」话题：聚合真实面试复盘与简历修改案例，信息密度高且去滤镜- B站「职场生存指南」合集：含HR视角解析JD关键词、大厂笔试真题拆解等实战内容国家图书馆·公开课平台：提供免费职业素养课（如《沟通力训练》《Excel高效办公》），结业可下载证书\n### 方案二：备选（能量低谷日计划）动线：全天仅启动1个「30分钟沉浸任务」（如听1期行业播客+记2个关键词）→傍晚30分钟「无目标散步」（关闭手机，观察5种颜色/声音）→ 睡前10分钟「成就回溯」（写下今日1件“我做了”的事，无论大小）\n> 亮点：彻底卸载目标压力，用感官锚定当下，阻断灾难化想象风险提示：禁止将此日等同于“摆烂”，其核心是修复执行功能，非放弃行动- 便利性：零工具依赖；散步可在家附近完成- 推荐地点说明：\n- 「得到·每天听本书」职场类专辑：单本解读15–30分钟，降低知识摄入门槛- Keep「办公室肩颈放松」课程：5分钟跟练，缓解久坐带来的躯体化焦虑\n- 微信读书「当代青年生存报告」书单：含《倦怠社会》《工作、消费主义和新穷人》等轻学术读物，提供宏观视角消解个人化羞耻五、复盘与下一步\n- 本轮完成标准：\n1.连续3天执行任一方案并记录「今日1件我做了的事」\n2.识别出自身焦虑最常触发的具体情境（如：刷新招聘APP后/接到家人询问时）\n- 下一步可执行项：\n- 下载「潮汐」App，设置每日晨间15分钟「焦虑翻译」提醒在小红书搜索「XX行业真实面试」（替换XX为意向领域），收藏3篇高赞笔记地点速览（便于配图，每条独立一行）：\n- 小红书求职话题：真实经验沉淀池 |无滤镜吐槽区 | JD关键词词典B站职场合集：HR亲授避坑指南 |笔试真题动画解析 | 大厂新人vlog国家图书馆公开课：权威免费认证 | 职业技能全覆盖 |学习进度可视化', NULL, NULL, '2026-05-28 21:29:35');

-- ----------------------------
-- Table structure for chat_session
-- ----------------------------
DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session`  (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `archived` tinyint(1) NOT NULL DEFAULT 0,
  `pinned` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_updated`(`user_id` ASC, `updated_at` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of chat_session
-- ----------------------------
INSERT INTO `chat_session` VALUES ('07ac0d3d-fb9d-415b-b9e2-c5e5a1eadae6', 3, '新会话', 'coach', 0, 0, '2026-05-28 21:22:06', '2026-05-28 21:22:06');
INSERT INTO `chat_session` VALUES ('59bfcda8-768c-4489-ac63-0e1bb1834cbf', 1, '睡前小决策', 'planner', 0, 1, '2026-05-23 22:18:23', '2026-05-28 21:27:04');
INSERT INTO `chat_session` VALUES ('8360f4d9-30ef-426d-a2d1-db8f6ffdb606', 1, '我现在感觉很焦虑，就业压力很大，', 'planner', 0, 0, '2026-05-28 21:28:34', '2026-05-28 21:29:35');

-- ----------------------------
-- Table structure for export_record
-- ----------------------------
DROP TABLE IF EXISTS `export_record`;
CREATE TABLE `export_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `source_message_id` bigint NULL DEFAULT NULL,
  `file_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of export_record
-- ----------------------------
INSERT INTO `export_record` VALUES (1, 1, 'b6202d9d-845a-4232-a7e8-a241b3bdbcb1', 33, 'D:\\Code\\Java\\Projects\\ai-agent/tmp/exports/plan-20260429-184351-289874cf.pdf', '2026-04-29 18:43:52');
INSERT INTO `export_record` VALUES (2, 1, 'b6202d9d-845a-4232-a7e8-a241b3bdbcb1', 37, 'D:\\Code\\Java\\Projects\\ai-agent/tmp/exports/plan-20260429-191013-9211a5dd.pdf', '2026-04-29 19:10:14');
INSERT INTO `export_record` VALUES (3, 1, 'b6202d9d-845a-4232-a7e8-a241b3bdbcb1', 49, 'D:\\Code\\Java\\Projects\\ai-agent/tmp/exports/plan-20260429-193754-149021e8.pdf', '2026-04-29 19:37:55');
INSERT INTO `export_record` VALUES (4, 1, 'd0112b6b-3b13-4b12-9eb9-4bd84b3c0331', 76, 'D:\\Code\\Java\\Projects\\ai-agent/tmp/exports/plan-20260429-234932-09154597.pdf', '2026-04-29 23:49:33');
INSERT INTO `export_record` VALUES (5, 1, 'e0c43868-ac8b-4945-a092-776d6c160d1d', 92, 'D:\\Code\\Java\\Projects\\ai-agent/tmp/exports/plan-20260430-132601-9de2a8f8.pdf', '2026-04-30 13:26:02');
INSERT INTO `export_record` VALUES (6, 1, 'e0c43868-ac8b-4945-a092-776d6c160d1d', 92, 'D:\\Code\\Java\\Projects\\ai-agent/tmp/exports/plan-20260430-132605-bc1bb311.pdf', '2026-04-30 13:26:05');
INSERT INTO `export_record` VALUES (7, 1, 'a86472eb-a7aa-4da6-9c9c-543f15ab32fb', 94, 'D:\\Code\\Java\\Projects\\ai-agent/tmp/exports/plan-20260430-133744-aa9b6b07.pdf', '2026-04-30 13:37:44');
INSERT INTO `export_record` VALUES (8, 1, '5eeef7aa-37c7-4ba5-92f4-a458ea50eb05', 98, 'D:\\Code\\Java\\Projects\\ai-agent/tmp/exports/plan-20260430-140243-5437f466.pdf', '2026-04-30 14:02:45');
INSERT INTO `export_record` VALUES (9, 1, '5eeef7aa-37c7-4ba5-92f4-a458ea50eb05', 98, 'D:\\Code\\Java\\Projects\\ai-agent/tmp/exports/plan-20260430-140246-8f7e6553.pdf', '2026-04-30 14:02:48');
INSERT INTO `export_record` VALUES (10, 1, '8693f101-e840-4907-b397-d3011b914ec2', 104, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-151502-41aa2bb7.pdf', '2026-04-30 15:15:36');
INSERT INTO `export_record` VALUES (11, 1, '8693f101-e840-4907-b397-d3011b914ec2', 104, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-151522-1d8181d2.pdf', '2026-04-30 15:15:47');
INSERT INTO `export_record` VALUES (12, 1, '8693f101-e840-4907-b397-d3011b914ec2', 104, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-151507-cfcdb8a4.pdf', '2026-04-30 15:15:52');
INSERT INTO `export_record` VALUES (13, 1, '8693f101-e840-4907-b397-d3011b914ec2', 104, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-151510-d40ca528.pdf', '2026-04-30 15:15:53');
INSERT INTO `export_record` VALUES (14, 1, '8693f101-e840-4907-b397-d3011b914ec2', 104, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-151518-422eea84.pdf', '2026-04-30 15:16:11');
INSERT INTO `export_record` VALUES (15, 1, '8693f101-e840-4907-b397-d3011b914ec2', 104, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-151536-1a135fe0.pdf', '2026-04-30 15:16:23');
INSERT INTO `export_record` VALUES (16, 1, '8693f101-e840-4907-b397-d3011b914ec2', 104, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-151520-6260ffa5.pdf', '2026-04-30 15:16:27');
INSERT INTO `export_record` VALUES (17, 1, '8693f101-e840-4907-b397-d3011b914ec2', 104, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-151552-ff783441.pdf', '2026-04-30 15:16:37');
INSERT INTO `export_record` VALUES (18, 1, '8693f101-e840-4907-b397-d3011b914ec2', 104, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-151623-e32da841.pdf', '2026-04-30 15:16:52');
INSERT INTO `export_record` VALUES (19, 1, '8693f101-e840-4907-b397-d3011b914ec2', 104, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-151611-d5cc997d.pdf', '2026-04-30 15:16:57');
INSERT INTO `export_record` VALUES (20, 1, '8693f101-e840-4907-b397-d3011b914ec2', 104, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-151622-1de4e006.pdf', '2026-04-30 15:17:02');
INSERT INTO `export_record` VALUES (21, 1, '8693f101-e840-4907-b397-d3011b914ec2', 104, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-151620-e4ea175f.pdf', '2026-04-30 15:17:13');
INSERT INTO `export_record` VALUES (22, 1, '8693f101-e840-4907-b397-d3011b914ec2', 104, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-151857-58a90c00.pdf', '2026-04-30 15:19:48');
INSERT INTO `export_record` VALUES (23, 1, 'f859fc1a-4122-4809-add5-05902397b41f', 115, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-170507-a637c81d.pdf', '2026-04-30 17:05:08');
INSERT INTO `export_record` VALUES (24, 1, 'f859fc1a-4122-4809-add5-05902397b41f', 115, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-170532-33c7a261.pdf', '2026-04-30 17:05:33');
INSERT INTO `export_record` VALUES (25, 1, 'f859fc1a-4122-4809-add5-05902397b41f', 115, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-170601-024e8e65.pdf', '2026-04-30 17:06:01');
INSERT INTO `export_record` VALUES (26, 1, '3e21410a-2c3d-42ff-ae33-bba6d211cfde', 113, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-170519-e3693d58.pdf', '2026-04-30 17:06:03');
INSERT INTO `export_record` VALUES (27, 1, '3e21410a-2c3d-42ff-ae33-bba6d211cfde', 113, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-170543-0d6c5c07.pdf', '2026-04-30 17:07:21');
INSERT INTO `export_record` VALUES (28, 1, '3e21410a-2c3d-42ff-ae33-bba6d211cfde', 113, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-171403-b3858880.pdf', '2026-04-30 17:14:54');
INSERT INTO `export_record` VALUES (29, 1, '3e21410a-2c3d-42ff-ae33-bba6d211cfde', 113, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-171437-26cefb48.pdf', '2026-04-30 17:15:25');
INSERT INTO `export_record` VALUES (30, 1, '3e21410a-2c3d-42ff-ae33-bba6d211cfde', 113, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-171453-2b105a36.pdf', '2026-04-30 17:16:03');
INSERT INTO `export_record` VALUES (31, 1, '3e21410a-2c3d-42ff-ae33-bba6d211cfde', 113, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-171706-2992647d.pdf', '2026-04-30 17:17:53');
INSERT INTO `export_record` VALUES (32, 1, '4b410e1e-d16d-4161-ba05-3ebf0d50bb42', 117, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260430-180510-eb48239c.pdf', '2026-04-30 18:05:11');
INSERT INTO `export_record` VALUES (33, 1, 'e76a99d7-98fd-4407-ab4e-790b03280ced', 157, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260504-183824-4425f453.pdf', '2026-05-04 18:38:25');
INSERT INTO `export_record` VALUES (34, 1, '1719898c-a9ac-4d2c-a3be-eb06f85794af', 163, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260505-204434-c9e520fe.pdf', '2026-05-05 20:44:35');
INSERT INTO `export_record` VALUES (35, 1, 'c7551fc2-8325-419e-b8b7-49a17f4ca00f', 167, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260506-170804-ca3c3604.pdf', '2026-05-06 17:08:05');
INSERT INTO `export_record` VALUES (36, 1, 'c7551fc2-8325-419e-b8b7-49a17f4ca00f', 167, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260506-172218-d8ea6a61.pdf', '2026-05-06 17:22:19');
INSERT INTO `export_record` VALUES (37, 1, '59bfcda8-768c-4489-ac63-0e1bb1834cbf', 187, 'D:\\Code\\Java\\Projects\\ai-agent\\ai-agent/tmp/exports/plan-20260524-133027-c93afaad.pdf', '2026-05-24 13:30:28');

-- ----------------------------
-- Table structure for user_account
-- ----------------------------
DROP TABLE IF EXISTS `user_account`;
CREATE TABLE `user_account`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `password_hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `nickname` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_account
-- ----------------------------
INSERT INTO `user_account` VALUES (1, 'Eddie', '$2a$10$L3nFekkGgQ8pHRkNZ9ktl.KX94SxEP44hVazDy.dXpiRnXGnemNcy', 'Eddie', 'https://assets.leetcode.cn/aliyun-lc-upload/users/vigorous-jennings4mh/avatar_1703062532.png', '2026-04-29 16:40:41', '2026-04-29 17:27:21');
INSERT INTO `user_account` VALUES (3, 'apikey:seed', '$2a$10$uFp9wdn54x2a9i1airIplOLdtLWFiI2Xjb5L2Qk4kCu64V4rBMSmO', 'apikey:seed', NULL, '2026-05-05 18:19:25', '2026-05-05 18:19:25');

SET FOREIGN_KEY_CHECKS = 1;
