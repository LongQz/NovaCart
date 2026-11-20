for index, key in ipairs(KEYS) do
    if redis.call('EXISTS', key) == 1 then
        redis.call('INCRBY', key, ARGV[index])
    end
end
return 0
