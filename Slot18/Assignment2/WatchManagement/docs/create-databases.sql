-- [TODO-2.1] Tạo 3 database (Database per Service) và bật login sa
CREATE DATABASE DBAccount;
CREATE DATABASE DBBrand;
CREATE DATABASE DBWatch;
GO
ALTER LOGIN sa ENABLE;
ALTER LOGIN sa WITH PASSWORD = 'YourStrong@Password123';
GO
