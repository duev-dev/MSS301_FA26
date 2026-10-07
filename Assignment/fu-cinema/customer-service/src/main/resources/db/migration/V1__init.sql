-- T-SQL (SQL Server): IDENTITY thay cho AUTO_INCREMENT, NVARCHAR de luu tieng Viet co dau
CREATE TABLE customer (
    customer_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_name     NVARCHAR(100) NOT NULL,
    telephone         VARCHAR(15),
    email             VARCHAR(100)  NOT NULL,
    customer_birthday DATE,
    customer_status   VARCHAR(20)   NOT NULL CONSTRAINT df_customer_status DEFAULT 'ACTIVE',
    password          VARCHAR(100)  NOT NULL,
    CONSTRAINT uk_customer_email UNIQUE (email)
);
