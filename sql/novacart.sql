CREATE DATABASE IF NOT EXISTS novacart
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE novacart;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS support_message;
DROP TABLE IF EXISTS review;
DROP TABLE IF EXISTS direct_message;
DROP TABLE IF EXISTS delivery_task;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS shop;
DROP TABLE IF EXISTS user_account;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(60) NOT NULL UNIQUE,
    password_hash CHAR(64) NOT NULL,
    nickname VARCHAR(80) NOT NULL,
    phone VARCHAR(20),
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO user_account (id, username, password_hash, nickname, phone, role) VALUES
    (1, 'demo', SHA2('123456', 256), '林小满', '13800000001', 'USER'),
    (2, 'qzh', SHA2('123456', 256), 'qzh', '15023631455', 'USER'),
    (3, 'developer', SHA2('admin123', 256), 'NovaCart 开发者', '13800000099', 'ADMIN');

CREATE TABLE direct_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_dm_conversation (from_user_id, to_user_id, id),
    INDEX idx_dm_inbox (to_user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO direct_message (from_user_id, to_user_id, content, is_read, created_at) VALUES
    (2, 1, '你好，我也在重庆，最近发现一家不错的茶餐厅。', 0, NOW() - INTERVAL 2 HOUR),
    (1, 2, '可以呀，把地址发我看看。', 1, NOW() - INTERVAL 100 MINUTE),
    (2, 1, '就在南岸区崇文路，周末一起去试试？', 0, NOW() - INTERVAL 80 MINUTE),
    (3, 1, '欢迎使用 NovaCart，有问题可以直接私信我。', 0, NOW() - INTERVAL 30 MINUTE);

CREATE TABLE shop (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    biz_type VARCHAR(16) NOT NULL,
    address VARCHAR(255) NOT NULL,
    rating DECIMAL(3,1) NOT NULL DEFAULT 5.0,
    delivery_minutes INT NOT NULL DEFAULT 30
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO shop (id, name, biz_type, address, rating, delivery_minutes) VALUES
    (1, 'Nova 生活选物', 'MALL', '重庆市南岸区数字生活中心', 4.9, 120),
    (2, '山城食堂', 'DELIVERY', '重庆市南岸区崇文路', 4.8, 28),
    (3, '晚风甜品铺', 'DELIVERY', '重庆市南岸区南山路', 4.9, 25),
    (4, '江畔轻食', 'DELIVERY', '重庆市南岸区江畔路', 4.7, 30),
    (5, '炭火小馆', 'DELIVERY', '重庆市南岸区学府路', 4.8, 35);

CREATE TABLE product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    category VARCHAR(60) NOT NULL,
    biz_type VARCHAR(16) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    sales INT NOT NULL DEFAULT 0,
    description VARCHAR(500),
    tags VARCHAR(255),
    image_url VARCHAR(500),
    active TINYINT(1) NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product_biz (biz_type, active),
    INDEX idx_product_shop (shop_id),
    INDEX idx_product_sales (sales)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO product (shop_id, name, category, biz_type, price, stock, sales, description, tags, image_url) VALUES
    (1, '降噪蓝牙耳机 AirLite', '数码', 'MALL', 299.00, 35, 812, '轻量头戴式耳机，支持主动降噪和 40 小时续航。', '数码,通勤,降噪', ''),
    (1, '柔光阅读台灯', '生活', 'MALL', 129.00, 48, 631, '无频闪暖光台灯，三档色温适合宿舍和书桌。', '生活,阅读,宿舍', ''),
    (1, '山野香薰蜡烛', '生活', 'MALL', 69.00, 80, 455, '雪松与柑橘香调，适合晚间放松。', '香薰,礼物,居家', ''),
    (1, '城市通勤双肩包', '出行', 'MALL', 189.00, 42, 529, '防泼水 18L 容量，可放 15 英寸电脑。', '通勤,电脑包,防泼水', ''),
    (1, '磁吸无线充电器', '数码', 'MALL', 99.00, 65, 720, '15W 磁吸无线充电，附 1 米编织线。', '数码,充电,桌面', ''),
    (1, '宿舍迷你加湿器', '生活', 'MALL', 79.00, 54, 388, '静音雾化，两档亮度，适合桌面使用。', '生活,宿舍,加湿', ''),
    (1, '轻量运动水杯', '出行', 'MALL', 49.00, 110, 985, 'Tritan 材质，500ml，可单手开盖。', '水杯,运动,便携', ''),
    (1, '机械键盘 K67', '数码', 'MALL', 239.00, 27, 342, '67 键热插拔机械键盘，支持蓝牙和有线连接。', '数码,键盘,办公', ''),
    (1, '桌面理线收纳盒', '生活', 'MALL', 39.00, 95, 465, '隐藏电源线与充电器，附可重复使用束线带。', '收纳,桌面,理线', ''),
    (1, '城市雨伞', '出行', 'MALL', 59.00, 0, 601, '自动折叠晴雨伞，防晒涂层。', '雨伞,便携,防晒', ''),
    (1, '轻薄移动电源', '数码', 'MALL', 159.00, 38, 534, '10000mAh，支持 22.5W 快充。', '数码,充电,出行', ''),
    (1, '舒适颈枕', '出行', 'MALL', 89.00, 31, 298, '记忆棉支撑，适合长途出行。', '出行,颈枕,旅行', ''),

    (2, '招牌山城小面', '主食', 'DELIVERY', 18.00, 120, 2350, '麻辣鲜香，可选微辣、中辣和重庆辣。', '面食,麻辣,午餐', ''),
    (2, '藤椒鸡腿饭', '主食', 'DELIVERY', 26.00, 75, 1480, '鲜麻藤椒汁配烤鸡腿与时蔬，可选不辣版本。', '米饭,藤椒,高蛋白,可选不辣', ''),
    (2, '清爽番茄肥牛汤', '汤品', 'DELIVERY', 32.00, 50, 980, '不辣番茄汤底，配肥牛、金针菇和豆腐。', '不辣,汤品,晚餐', ''),
    (2, '酸辣土豆丝盖饭', '主食', 'DELIVERY', 22.00, 66, 1265, '酸辣开胃，配米饭和时蔬。', '米饭,酸辣,午餐', ''),
    (2, '重庆毛血旺', '特色菜', 'DELIVERY', 48.00, 28, 870, '经典麻辣口味，含鸭血、毛肚和豆芽。', '麻辣,特色菜,晚餐', ''),
    (4, '低温鸡胸沙拉', '轻食', 'DELIVERY', 29.00, 43, 760, '低温鸡胸、生菜、玉米和低脂油醋汁。', '轻食,不辣,健身', ''),
    (4, '牛油果鸡蛋卷', '轻食', 'DELIVERY', 24.00, 52, 540, '全麦饼皮，牛油果与水煮蛋，不辣。', '轻食,早餐,不辣', ''),
    (4, '南瓜浓汤', '汤品', 'DELIVERY', 18.00, 60, 620, '奶油南瓜浓汤，口感清甜，不辣。', '汤品,不辣,早餐', ''),
    (3, '桂花酒酿小圆子', '甜品', 'DELIVERY', 16.00, 90, 1320, '低甜桂花酒酿，温热口感。', '甜品,低甜,夜宵', ''),
    (3, '生椰拿铁', '饮品', 'DELIVERY', 19.00, 110, 1850, '椰乳与浓缩咖啡，可选少冰。', '饮品,咖啡,生椰', ''),
    (3, '轻乳酪蛋糕', '甜品', 'DELIVERY', 24.00, 60, 990, '轻盈乳酪口感，适合两到三人分享。', '甜品,分享,下午茶', ''),
    (5, '炭火烤鸡腿', '烧烤', 'DELIVERY', 25.00, 39, 820, '炭火现烤，可选微辣或不辣。', '烧烤,鸡腿,夜宵', '');

CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    request_key VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    biz_type VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    recipient VARCHAR(80),
    phone VARCHAR(20),
    address VARCHAR(255),
    note VARCHAR(500),
    cancel_reason VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at DATETIME,
    version INT NOT NULL DEFAULT 0,
    INDEX idx_orders_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_callback_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    callback_id VARCHAR(96) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    payload VARCHAR(1000),
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_payment_callback_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    INDEX idx_order_item_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE delivery_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    recipient VARCHAR(80),
    phone VARCHAR(20),
    address VARCHAR(255) NOT NULL,
    courier_name VARCHAR(80),
    status VARCHAR(32) NOT NULL,
    eta_minutes INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_delivery_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL UNIQUE,
    product_id BIGINT,
    rating INT NOT NULL,
    content VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_review_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE support_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_id BIGINT,
    sender_role VARCHAR(16) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_support_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
