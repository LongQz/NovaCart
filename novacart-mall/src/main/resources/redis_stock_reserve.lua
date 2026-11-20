for index, key in ipairs(KEYS) do
    local stock = tonumber(redis.call('GET', key) or '-1')
    local quantity = tonumber(ARGV[index])
    if stock < quantity then
        for rollback = 1, index - 1 do
            redis.call('INCRBY', KEYS[rollback], ARGV[rollback])
        end
        return index
    end
    redis.call('DECRBY', key, quantity)
end
return 0
