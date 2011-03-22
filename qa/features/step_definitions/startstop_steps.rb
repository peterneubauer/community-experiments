Given /^Neo4j Server is (not )?running$/ do |negate|
  if (current_platform.unix?)
    puts `#{neo4j.home}/bin/neo4j status`
    $? == (negate == 'not ' ? 256 : 0)
  elsif (current_platform.windows?)
    puts `#{neo4j.home}\\bin\\wrapper-windows-x86-32.exe -q ..\\conf\\neo4j-wrapper.conf`
    puts "result #{$?} "
    # fail "failed #{$?} " if $?!= 0
    fail "not implemented"
  else
    fail 'platform not supported'
  end

end


Then /^I (start|stop) Neo4j Server$/ do |action|
  if (current_platform.unix?)
    puts `#{neo4j.home}/bin/neo4j #{action}`
    fail "already running" if $? == 256
    fail "unknown return code #{$?} " if $?!= 0
    sleep 20
  elsif (current_platform.windows?)
    puts `#{neo4j.home}\\bin\\wrapper-windows-x86-32.exe #{ action == 'start' ? '-it' : '-r' } ..\\conf\\neo4j-wrapper.conf`
    fail "failed #{$?} " if $?!= 0
  else
    fail 'platform not supported'
  end
end


Then /^"([^"]*)" should (not)? ?provide the Neo4j REST interface$/ do |uri, negate|
  begin
    response = Net::HTTP.get_response(URI.parse(uri))
  rescue Exception=>e
    fail "REST-interface is not running #{e}" if e && negate != 'not'
  end
  fail 'REST-interface is not running' if negate == nil && response && response.code.to_i != 200
  fail 'REST-interface is running' if negate == 'not' && response && response.code.to_i == 200
end

