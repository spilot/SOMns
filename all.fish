#! /usr/bin/fish

# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns CD 1 0 2 > all/CD.txt
# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns DeltaBlue 1 0 200 > all/DeltaBlue.txt
# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Havlak 1 0 2 > all/Havlak.txt
# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Json 1 0 2 > all/Json.txt
# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Richards 1 0 2 > all/Richards.txt
# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Bounce 1 0 20 > all/Bounce.txt
# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns List 1 0 50 > all/List.txt
# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Mandelbrot 1 0 50 > all/Mandelbrot.txt
# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns NBody 1 0 10000 > all/NBody.txt
# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Permute 1 0 10 > all/Permute.txt
# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Queens 1 0 10 > all/Queens.txt
# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Storage 1 0 20 > all/Storage.txt
# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Sieve 1 0 100 > all/Sieve.txt
# ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Towers 1 0 20 > all/Towers.txt

fish -c 'echo "Starting CD Benchmark"         ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns CD 130 0 100 > all4/CD.txt & '
fish -c 'echo "Starting DeltaBlue Benchmark"  ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns DeltaBlue 250 0 1000 > all4/DeltaBlue.txt &'
fish -c 'echo "Starting Havlak Benchmark"     ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Havlak 130 0 5 > all4/Havlak.txt'
fish -c 'echo "Starting Json Benchmark"       ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Json 120 0 50 > all4/Json.txt & '
fish -c 'echo "Starting Richards Benchmark"   ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Richards 130 0 60 > all4/Richards.txt & '
fish -c 'echo "Starting Bounce Benchmark"     ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Bounce 60 0 1000 > all4/Bounce.txt'
fish -c 'echo "Starting List Benchmark"       ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns List 70 0 1000 > all4/List.txt & '
fish -c 'echo "Starting Mandelbrot Benchmark" ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Mandelbrot 110 0 400 > all4/Mandelbrot.txt & '
fish -c 'echo "Starting NBody Benchmark"      ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns NBody 120 0 150000 > all4/NBody.txt'
fish -c 'echo "Starting Permute Benchmark"    ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Permute 60 0 500 > all4/Permute.txt &'
fish -c 'echo "Starting Queens Benchmark"     ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Queens 120 0 400 > all4/Queens.txt & '
fish -c 'echo "Starting Storage Benchmark"    ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Storage 75 0 1000 > all4/Storage.txt'
fish -c 'echo "Starting Sieve Benchmark"      ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Sieve 60 0 400 > all4/Sieve.txt &'
fish -c 'echo "Starting Towers Benchmark"     ; ./som -G -Dtruffle.TraceRewrites=true core-lib/Benchmarks/Harness.ns Towers 60 0 300 > all4/Towers.txt &'
