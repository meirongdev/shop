UPDATE user_account
SET password_hash = '{bcrypt}$2a$10$lV1eEiDHx.5RvAkc5xs0bOYeh22uCNkTqcwn/4D5jJJ/WjgiuEU0u'
WHERE username IN ('buyer.demo', 'buyer.vip', 'seller.demo')
  AND (
      password_hash IS NULL
      OR password_hash = ''
      OR password_hash LIKE '{bcrypt}$2a$10$dummyhashnotusedfordemousers%'
  );
