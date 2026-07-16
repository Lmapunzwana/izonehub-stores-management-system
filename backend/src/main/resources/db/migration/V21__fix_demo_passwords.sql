-- Update demo passwords to the correct hash for 'password123'
UPDATE app_users 
SET password_hash = '$2b$10$/ovhITo0GKrB80taBhejiO3tJqABy3w6fSJd4ap1vv/DcqIf0CVyi' 
WHERE email IN ('admin@demo.com', 'central@demo.com', 'site@demo.com');
